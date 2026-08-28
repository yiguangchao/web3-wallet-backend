// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {TestBase} from "./TestBase.sol";
import {SecureStakingVault} from "../src/SecureStakingVault.sol";
import {MockERC20Permit} from "../src/mocks/MockERC20Permit.sol";
import {IERC20} from "../src/interfaces/IERC20.sol";

interface IStakeTarget {
    function stake(uint256 amount) external;
}

contract ReentrantStakeToken is IERC20 {
    uint256 public override totalSupply;
    mapping(address => uint256) public override balanceOf;
    mapping(address => mapping(address => uint256)) public override allowance;
    IStakeTarget public target;
    bool public attack;
    bool public reentryBlocked;

    function mint(address to, uint256 amount) external {
        totalSupply += amount;
        balanceOf[to] += amount;
    }

    function setTarget(IStakeTarget value) external {
        target = value;
        attack = true;
    }

    function approve(address spender, uint256 amount) external returns (bool) {
        allowance[msg.sender][spender] = amount;
        return true;
    }

    function transfer(address to, uint256 amount) external returns (bool) {
        balanceOf[msg.sender] -= amount;
        balanceOf[to] += amount;
        return true;
    }

    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        allowance[from][msg.sender] -= amount;
        if (attack) {
            try target.stake(1) {
                revert("reentry unexpectedly succeeded");
            } catch (bytes memory reason) {
                bytes4 selector;
                assembly {
                    selector := mload(add(reason, 32))
                }
                require(selector == SecureStakingVault.ReentrantCall.selector, "wrong guard");
                reentryBlocked = true;
            }
        }
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }
}

contract SecureStakingVaultTest is TestBase {
    uint256 private constant ALICE_KEY = 0xA11CE;
    uint256 private constant REWARD_DURATION = 7 days;
    address private alice;
    address private bob = address(0xB0B);
    address private owner = address(0xA0);
    address private distributor = address(0xD1);

    MockERC20Permit private stakeToken;
    MockERC20Permit private rewardToken;
    SecureStakingVault private vault;

    function setUp() public {
        alice = vm.addr(ALICE_KEY);
        stakeToken = new MockERC20Permit("Stake Token", "STK");
        rewardToken = new MockERC20Permit("Reward Token", "RWD");
        vault = new SecureStakingVault(
            IERC20(address(stakeToken)), IERC20(address(rewardToken)), owner, distributor, REWARD_DURATION
        );
        stakeToken.mint(alice, 1_000_000 ether);
        stakeToken.mint(bob, 1_000_000 ether);
        rewardToken.mint(distributor, 10_000_000 ether);
        vm.prank(alice);
        stakeToken.approve(address(vault), type(uint256).max);
        vm.prank(bob);
        stakeToken.approve(address(vault), type(uint256).max);
        vm.prank(distributor);
        rewardToken.approve(address(vault), type(uint256).max);
    }

    function testStakeAccrueClaimAndExit() public {
        vm.prank(alice);
        vault.stake(100 ether);
        vm.prank(distributor);
        vault.notifyRewardAmount(700 ether);

        vm.warp(block.timestamp + 1 days);
        assertApproxEqAbs(vault.earned(alice), 100 ether, 1e6, "one day reward");

        vm.prank(alice);
        vault.exit();
        assertEq(vault.balanceOf(alice), 0, "stake cleared");
        assertEq(vault.totalStaked(), 0, "total cleared");
        assertEq(stakeToken.balanceOf(alice), 1_000_000 ether, "principal returned");
        assertApproxEqAbs(rewardToken.balanceOf(alice), 100 ether, 1e6, "reward paid");
    }

    function testStakeWithEip2612Permit() public {
        uint256 amount = 25 ether;
        uint256 deadline = block.timestamp + 1 hours;
        bytes32 structHash = keccak256(
            abi.encode(stakeToken.PERMIT_TYPEHASH(), alice, address(vault), amount, stakeToken.nonces(alice), deadline)
        );
        bytes32 digest = keccak256(abi.encodePacked("\x19\x01", stakeToken.DOMAIN_SEPARATOR(), structHash));
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(ALICE_KEY, digest);

        vm.prank(alice);
        vault.stakeWithPermit(amount, deadline, v, r, s);
        assertEq(vault.balanceOf(alice), amount, "permit stake credited");
        assertEq(stakeToken.allowance(alice, address(vault)), 0, "permit consumed");
    }

    function testRewardsAreProportionalAcrossInterleavedUsers() public {
        vm.prank(alice);
        vault.stake(100 ether);
        vm.prank(distributor);
        vault.notifyRewardAmount(700 ether);
        vm.warp(block.timestamp + 1 days);
        vm.prank(bob);
        vault.stake(100 ether);
        vm.warp(block.timestamp + 1 days);

        assertApproxEqAbs(vault.earned(alice), 150 ether, 2e6, "alice weighted reward");
        assertApproxEqAbs(vault.earned(bob), 50 ether, 2e6, "bob weighted reward");
    }

    function testPauseBlocksNewRiskButPreservesWithdrawal() public {
        vm.prank(alice);
        vault.stake(10 ether);
        vm.prank(owner);
        vault.pause();

        vm.expectRevert(SecureStakingVault.ContractPaused.selector);
        vm.prank(bob);
        vault.stake(1 ether);

        vm.prank(alice);
        vault.withdraw(10 ether);
        assertEq(stakeToken.balanceOf(alice), 1_000_000 ether, "escape hatch works");
    }

    function testEmergencyWithdrawalForfeitsRewardButReturnsPrincipal() public {
        vm.prank(alice);
        vault.stake(10 ether);
        vm.prank(distributor);
        vault.notifyRewardAmount(700 ether);
        vm.warp(block.timestamp + 1 days);
        vm.prank(owner);
        vault.pause();

        vm.prank(alice);
        vault.emergencyWithdraw();
        assertEq(vault.balanceOf(alice), 0, "principal position cleared");
        assertEq(vault.rewards(alice), 0, "reward forfeited");
        assertEq(stakeToken.balanceOf(alice), 1_000_000 ether, "principal returned");
    }

    function testRejectsFeeOnTransferStakeToken() public {
        stakeToken.setFeeBps(100);
        vm.expectRevert(SecureStakingVault.FeeOnTransferNotSupported.selector);
        vm.prank(alice);
        vault.stake(100 ether);
        assertEq(vault.totalStaked(), 0, "revert preserves accounting");
    }

    function testRewardDistributorChangeRequiresDelay() public {
        vm.prank(owner);
        vault.proposeRewardDistributor(bob);
        vm.expectRevert(SecureStakingVault.ConfigurationDelayActive.selector);
        vm.prank(owner);
        vault.activateRewardDistributor();

        vm.warp(block.timestamp + vault.CONFIGURATION_DELAY());
        vm.prank(owner);
        vault.activateRewardDistributor();
        assertEq(vault.rewardDistributor(), bob, "distributor activated");
    }

    function testOwnershipRequiresAcceptanceByCandidate() public {
        vm.prank(owner);
        vault.transferOwnership(bob);
        vm.expectRevert(SecureStakingVault.NoPendingOwner.selector);
        vm.prank(alice);
        vault.acceptOwnership();
        vm.prank(bob);
        vault.acceptOwnership();
        assertEq(vault.owner(), bob, "ownership accepted");
    }

    function testCannotRecoverPrincipalOrRewardTokens() public {
        vm.expectRevert(SecureStakingVault.ProtectedToken.selector);
        vm.prank(owner);
        vault.recoverUnsupportedToken(IERC20(address(stakeToken)), owner, 1);
        vm.expectRevert(SecureStakingVault.ProtectedToken.selector);
        vm.prank(owner);
        vault.recoverUnsupportedToken(IERC20(address(rewardToken)), owner, 1);
    }

    function testReentrantStakeIsRejectedAndRollsBack() public {
        ReentrantStakeToken hostile = new ReentrantStakeToken();
        SecureStakingVault guarded = new SecureStakingVault(
            IERC20(address(hostile)), IERC20(address(rewardToken)), owner, distributor, REWARD_DURATION
        );
        hostile.mint(alice, 10 ether);
        vm.prank(alice);
        hostile.approve(address(guarded), type(uint256).max);
        hostile.setTarget(IStakeTarget(address(guarded)));

        vm.prank(alice);
        guarded.stake(1 ether);
        assertTrue(hostile.reentryBlocked(), "nested stake was rejected");
        assertEq(guarded.totalStaked(), 1 ether, "outer stake completed");
    }

    function testRejectsInvalidConstruction() public {
        vm.expectRevert(SecureStakingVault.InvalidTokenPair.selector);
        new SecureStakingVault(
            IERC20(address(stakeToken)), IERC20(address(stakeToken)), owner, distributor, REWARD_DURATION
        );
        vm.expectRevert(SecureStakingVault.InvalidDuration.selector);
        new SecureStakingVault(IERC20(address(stakeToken)), IERC20(address(rewardToken)), owner, distributor, 1);
        vm.expectRevert(SecureStakingVault.ZeroAddress.selector);
        new SecureStakingVault(
            IERC20(address(stakeToken)), IERC20(address(rewardToken)), address(0), distributor, REWARD_DURATION
        );
    }

    function testRejectsZeroAndExcessiveOperations() public {
        vm.expectRevert(SecureStakingVault.ZeroAmount.selector);
        vm.prank(alice);
        vault.stake(0);
        vm.expectRevert(SecureStakingVault.InsufficientStake.selector);
        vm.prank(alice);
        vault.withdraw(1);
        vm.expectRevert(SecureStakingVault.ContractNotPaused.selector);
        vm.prank(alice);
        vault.emergencyWithdraw();
    }

    function testRejectsUnauthorizedAdministrationAndRewardFunding() public {
        vm.expectRevert(SecureStakingVault.Unauthorized.selector);
        vm.prank(alice);
        vault.pause();
        vm.expectRevert(SecureStakingVault.Unauthorized.selector);
        vm.prank(alice);
        vault.notifyRewardAmount(1 ether);

        vm.expectRevert(SecureStakingVault.RewardRateTooLow.selector);
        vm.prank(distributor);
        vault.notifyRewardAmount(1);

        rewardToken.setFeeBps(100);
        vm.expectRevert(SecureStakingVault.FeeOnTransferNotSupported.selector);
        vm.prank(distributor);
        vault.notifyRewardAmount(700 ether);
    }

    function testPauseLifecycleAndUnsupportedTokenRecovery() public {
        MockERC20Permit accidental = new MockERC20Permit("Accidental", "ACC");
        accidental.mint(address(vault), 3 ether);
        vm.prank(owner);
        vault.recoverUnsupportedToken(IERC20(address(accidental)), bob, 3 ether);
        assertEq(accidental.balanceOf(bob), 3 ether, "unrelated token recovered");

        vm.prank(owner);
        vault.pause();
        vm.prank(owner);
        vault.unpause();
        assertTrue(!vault.paused(), "vault unpaused");
    }

    function testFuzzStakeWithdrawPreservesPrincipal(uint96 rawStake, uint96 rawWithdraw) public {
        uint256 stakeAmount = bound(rawStake, 1, 1_000_000 ether);
        uint256 withdrawAmount = bound(rawWithdraw, 1, stakeAmount);
        vm.prank(alice);
        vault.stake(stakeAmount);
        vm.prank(alice);
        vault.withdraw(withdrawAmount);

        assertEq(vault.balanceOf(alice), stakeAmount - withdrawAmount, "user balance");
        assertEq(vault.totalStaked(), stakeAmount - withdrawAmount, "total stake");
        assertEq(stakeToken.balanceOf(address(vault)), stakeAmount - withdrawAmount, "solvent");
    }
}
