// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {SecureStakingVault} from "../src/SecureStakingVault.sol";
import {IERC20} from "../src/interfaces/IERC20.sol";

interface ScriptVm {
    function envAddress(string calldata name) external returns (address);
    function envUint(string calldata name) external returns (uint256);
    function startBroadcast(uint256 privateKey) external;
    function stopBroadcast() external;
}

contract DeploySecureStakingVault {
    ScriptVm private constant vm = ScriptVm(address(uint160(uint256(keccak256("hevm cheat code")))));

    function run() external returns (SecureStakingVault vault) {
        uint256 deployerKey = vm.envUint("DEPLOYER_PRIVATE_KEY");
        address stakeToken = vm.envAddress("STAKING_TOKEN_ADDRESS");
        address rewardToken = vm.envAddress("REWARD_TOKEN_ADDRESS");
        address owner = vm.envAddress("VAULT_OWNER_ADDRESS");
        address distributor = vm.envAddress("REWARD_DISTRIBUTOR_ADDRESS");
        uint256 duration = vm.envUint("REWARD_DURATION_SECONDS");
        vm.startBroadcast(deployerKey);
        vault = new SecureStakingVault(IERC20(stakeToken), IERC20(rewardToken), owner, distributor, duration);
        vm.stopBroadcast();
    }
}
