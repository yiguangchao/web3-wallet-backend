// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {TestBase} from "./TestBase.sol";
import {SecureStakingVault} from "../src/SecureStakingVault.sol";
import {MockERC20Permit} from "../src/mocks/MockERC20Permit.sol";
import {IERC20} from "../src/interfaces/IERC20.sol";

contract StakingHandler is TestBase {
    SecureStakingVault public immutable vault;
    MockERC20Permit public immutable stakeToken;
    MockERC20Permit public immutable rewardToken;
    address[3] public actors;

    constructor(SecureStakingVault vault_, MockERC20Permit stakeToken_, MockERC20Permit rewardToken_) {
        vault = vault_;
        stakeToken = stakeToken_;
        rewardToken = rewardToken_;
        actors = [address(0x1001), address(0x1002), address(0x1003)];
        for (uint256 i; i < actors.length; ++i) {
            stakeToken.mint(actors[i], 1_000_000 ether);
            vm.prank(actors[i]);
            stakeToken.approve(address(vault), type(uint256).max);
        }
        rewardToken.approve(address(vault), type(uint256).max);
    }

    function stake(uint256 actorSeed, uint96 rawAmount) external {
        address actor = actors[actorSeed % actors.length];
        uint256 available = stakeToken.balanceOf(actor);
        if (available == 0) return;
        uint256 amount = bound(rawAmount, 1, available);
        vm.prank(actor);
        vault.stake(amount);
    }

    function withdraw(uint256 actorSeed, uint96 rawAmount) external {
        address actor = actors[actorSeed % actors.length];
        uint256 staked = vault.balanceOf(actor);
        if (staked == 0) return;
        uint256 amount = bound(rawAmount, 1, staked);
        vm.prank(actor);
        vault.withdraw(amount);
    }

    function claim(uint256 actorSeed) external {
        vm.prank(actors[actorSeed % actors.length]);
        vault.getReward();
    }

    function notify(uint96 rawAmount) external {
        uint256 amount = bound(rawAmount, 1 days, 1_000_000 ether);
        rewardToken.mint(address(this), amount);
        vault.notifyRewardAmount(amount);
    }

    function advanceTime(uint32 rawSeconds) external {
        vm.warp(block.timestamp + bound(rawSeconds, 1, 30 days));
    }
}

contract SecureStakingVaultInvariantTest is TestBase {
    SecureStakingVault private vault;
    MockERC20Permit private stakeToken;
    MockERC20Permit private rewardToken;
    StakingHandler private handler;

    function setUp() public {
        stakeToken = new MockERC20Permit("Stake Token", "STK");
        rewardToken = new MockERC20Permit("Reward Token", "RWD");
        vault = new SecureStakingVault(
            IERC20(address(stakeToken)), IERC20(address(rewardToken)), address(this), address(this), 7 days
        );
        handler = new StakingHandler(vault, stakeToken, rewardToken);
        vm.prank(address(this));
        vault.proposeRewardDistributor(address(handler));
        vm.warp(block.timestamp + vault.CONFIGURATION_DELAY());
        vm.prank(address(this));
        vault.activateRewardDistributor();
    }

    function invariantPrincipalIsFullyBacked() public view {
        assertTrue(stakeToken.balanceOf(address(vault)) >= vault.totalStaked(), "principal backing");
    }

    function targetContracts() public view returns (address[] memory targets) {
        targets = new address[](1);
        targets[0] = address(handler);
    }

    function invariantUserStakesSumToTotal() public view {
        uint256 sum;
        for (uint256 i; i < 3; ++i) {
            sum += vault.balanceOf(handler.actors(i));
        }
        assertEq(sum, vault.totalStaked(), "position sum");
    }

    function invariantFutureRewardsRemainFunded() public view {
        uint256 remaining =
            block.timestamp < vault.periodFinish() ? (vault.periodFinish() - block.timestamp) * vault.rewardRate() : 0;
        assertTrue(rewardToken.balanceOf(address(vault)) >= remaining, "future reward reserve");
    }
}
