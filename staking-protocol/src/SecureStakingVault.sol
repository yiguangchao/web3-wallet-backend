// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {IERC20, IERC20Permit} from "./interfaces/IERC20.sol";
import {SafeToken} from "./libraries/SafeToken.sol";

/// @title SecureStakingVault
/// @notice Non-custodial staking vault with continuously streamed ERC-20 rewards.
/// @dev The stake and reward tokens must differ so principal can never fund rewards.
contract SecureStakingVault {
    using SafeToken for IERC20;

    error Unauthorized();
    error ZeroAddress();
    error ZeroAmount();
    error InvalidTokenPair();
    error InvalidDuration();
    error ContractPaused();
    error ContractNotPaused();
    error InsufficientStake();
    error FeeOnTransferNotSupported();
    error RewardRateTooLow();
    error RewardReserveInsufficient();
    error ReentrantCall();
    error NoPendingOwner();
    error ConfigurationDelayActive();
    error ProtectedToken();

    event Staked(address indexed user, uint256 amount);
    event Withdrawn(address indexed user, uint256 amount);
    event RewardPaid(address indexed user, uint256 reward);
    event RewardAdded(uint256 reward, uint256 rewardRate, uint256 periodFinish);
    event EmergencyWithdrawal(address indexed user, uint256 principal, uint256 forfeitedReward);
    event Paused(address indexed actor);
    event Unpaused(address indexed actor);
    event OwnershipTransferStarted(address indexed owner, address indexed pendingOwner);
    event OwnershipTransferred(address indexed previousOwner, address indexed newOwner);
    event RewardDistributorProposed(address indexed candidate, uint256 activatesAt);
    event RewardDistributorChanged(address indexed previousDistributor, address indexed newDistributor);
    event UnsupportedTokenRecovered(address indexed token, address indexed recipient, uint256 amount);

    uint256 public constant CONFIGURATION_DELAY = 2 days;
    uint256 private constant PRECISION = 1e18;

    IERC20 public immutable stakingToken;
    IERC20 public immutable rewardToken;
    uint256 public immutable rewardsDuration;

    address public owner;
    address public pendingOwner;
    address public rewardDistributor;
    address public proposedRewardDistributor;
    uint256 public rewardDistributorActivationTime;
    bool public paused;

    uint256 public totalStaked;
    mapping(address => uint256) public balanceOf;

    uint256 public periodFinish;
    uint256 public rewardRate;
    uint256 public lastUpdateTime;
    uint256 public rewardPerTokenStored;
    mapping(address => uint256) public userRewardPerTokenPaid;
    mapping(address => uint256) public rewards;

    uint256 private lockState = 1;

    constructor(
        IERC20 stakingToken_,
        IERC20 rewardToken_,
        address owner_,
        address rewardDistributor_,
        uint256 rewardsDuration_
    ) {
        if (
            address(stakingToken_) == address(0) || address(rewardToken_) == address(0) || owner_ == address(0)
                || rewardDistributor_ == address(0)
        ) revert ZeroAddress();
        if (address(stakingToken_) == address(rewardToken_)) revert InvalidTokenPair();
        if (rewardsDuration_ < 1 hours || rewardsDuration_ > 365 days) revert InvalidDuration();
        stakingToken = stakingToken_;
        rewardToken = rewardToken_;
        owner = owner_;
        rewardDistributor = rewardDistributor_;
        rewardsDuration = rewardsDuration_;
        emit OwnershipTransferred(address(0), owner_);
        emit RewardDistributorChanged(address(0), rewardDistributor_);
    }

    modifier onlyOwner() {
        if (msg.sender != owner) revert Unauthorized();
        _;
    }

    modifier onlyRewardDistributor() {
        if (msg.sender != rewardDistributor) revert Unauthorized();
        _;
    }

    modifier whenNotPaused() {
        if (paused) revert ContractPaused();
        _;
    }

    modifier nonReentrant() {
        if (lockState != 1) revert ReentrantCall();
        lockState = 2;
        _;
        lockState = 1;
    }

    modifier updateReward(address account) {
        rewardPerTokenStored = rewardPerToken();
        lastUpdateTime = lastTimeRewardApplicable();
        if (account != address(0)) {
            rewards[account] = earned(account);
            userRewardPerTokenPaid[account] = rewardPerTokenStored;
        }
        _;
    }

    function lastTimeRewardApplicable() public view returns (uint256) {
        return block.timestamp < periodFinish ? block.timestamp : periodFinish;
    }

    function rewardPerToken() public view returns (uint256) {
        if (totalStaked == 0) return rewardPerTokenStored;
        return
            rewardPerTokenStored + ((lastTimeRewardApplicable() - lastUpdateTime) * rewardRate * PRECISION)
                / totalStaked;
    }

    function earned(address account) public view returns (uint256) {
        return
            (balanceOf[account] * (rewardPerToken() - userRewardPerTokenPaid[account])) / PRECISION + rewards[account];
    }

    function stake(uint256 amount) external nonReentrant whenNotPaused updateReward(msg.sender) {
        _stake(msg.sender, amount);
    }

    function stakeWithPermit(uint256 amount, uint256 deadline, uint8 v, bytes32 r, bytes32 s)
        external
        nonReentrant
        whenNotPaused
        updateReward(msg.sender)
    {
        IERC20Permit(address(stakingToken)).permit(msg.sender, address(this), amount, deadline, v, r, s);
        _stake(msg.sender, amount);
    }

    function withdraw(uint256 amount) external nonReentrant updateReward(msg.sender) {
        _withdraw(msg.sender, amount);
    }

    function getReward() external nonReentrant updateReward(msg.sender) {
        _claim(msg.sender);
    }

    function exit() external nonReentrant updateReward(msg.sender) {
        uint256 principal = balanceOf[msg.sender];
        if (principal != 0) _withdraw(msg.sender, principal);
        _claim(msg.sender);
    }

    /// @notice Lets users recover principal while paused and deliberately forfeit accrued rewards.
    function emergencyWithdraw() external nonReentrant updateReward(msg.sender) {
        if (!paused) revert ContractNotPaused();
        uint256 principal = balanceOf[msg.sender];
        if (principal == 0) revert ZeroAmount();
        uint256 forfeited = rewards[msg.sender];
        rewards[msg.sender] = 0;
        _withdraw(msg.sender, principal);
        emit EmergencyWithdrawal(msg.sender, principal, forfeited);
    }

    function notifyRewardAmount(uint256 amount)
        external
        nonReentrant
        onlyRewardDistributor
        whenNotPaused
        updateReward(address(0))
    {
        if (amount == 0) revert ZeroAmount();
        uint256 beforeBalance = rewardToken.balanceOf(address(this));
        rewardToken.safeTransferFrom(msg.sender, address(this), amount);
        uint256 received = rewardToken.balanceOf(address(this)) - beforeBalance;
        if (received != amount) revert FeeOnTransferNotSupported();

        uint256 distributable = amount;
        if (block.timestamp < periodFinish) {
            distributable += (periodFinish - block.timestamp) * rewardRate;
        }
        uint256 nextRate = distributable / rewardsDuration;
        if (nextRate == 0) revert RewardRateTooLow();
        if (nextRate * rewardsDuration > rewardToken.balanceOf(address(this))) {
            revert RewardReserveInsufficient();
        }
        rewardRate = nextRate;
        lastUpdateTime = block.timestamp;
        periodFinish = block.timestamp + rewardsDuration;
        emit RewardAdded(amount, nextRate, periodFinish);
    }

    function pause() external onlyOwner {
        if (paused) revert ContractPaused();
        paused = true;
        emit Paused(msg.sender);
    }

    function unpause() external onlyOwner {
        if (!paused) revert ContractNotPaused();
        paused = false;
        emit Unpaused(msg.sender);
    }

    function transferOwnership(address candidate) external onlyOwner {
        if (candidate == address(0)) revert ZeroAddress();
        pendingOwner = candidate;
        emit OwnershipTransferStarted(owner, candidate);
    }

    function acceptOwnership() external {
        if (msg.sender != pendingOwner || pendingOwner == address(0)) revert NoPendingOwner();
        address previousOwner = owner;
        owner = pendingOwner;
        pendingOwner = address(0);
        emit OwnershipTransferred(previousOwner, owner);
    }

    function proposeRewardDistributor(address candidate) external onlyOwner {
        if (candidate == address(0)) revert ZeroAddress();
        proposedRewardDistributor = candidate;
        rewardDistributorActivationTime = block.timestamp + CONFIGURATION_DELAY;
        emit RewardDistributorProposed(candidate, rewardDistributorActivationTime);
    }

    function activateRewardDistributor() external onlyOwner {
        address candidate = proposedRewardDistributor;
        if (candidate == address(0)) revert ZeroAddress();
        if (block.timestamp < rewardDistributorActivationTime) revert ConfigurationDelayActive();
        address previous = rewardDistributor;
        rewardDistributor = candidate;
        proposedRewardDistributor = address(0);
        rewardDistributorActivationTime = 0;
        emit RewardDistributorChanged(previous, candidate);
    }

    function recoverUnsupportedToken(IERC20 token, address recipient, uint256 amount) external onlyOwner nonReentrant {
        if (address(token) == address(stakingToken) || address(token) == address(rewardToken)) {
            revert ProtectedToken();
        }
        if (recipient == address(0)) revert ZeroAddress();
        token.safeTransfer(recipient, amount);
        emit UnsupportedTokenRecovered(address(token), recipient, amount);
    }

    function _stake(address account, uint256 amount) private {
        if (amount == 0) revert ZeroAmount();
        uint256 beforeBalance = stakingToken.balanceOf(address(this));
        stakingToken.safeTransferFrom(account, address(this), amount);
        uint256 received = stakingToken.balanceOf(address(this)) - beforeBalance;
        if (received != amount) revert FeeOnTransferNotSupported();
        totalStaked += amount;
        balanceOf[account] += amount;
        emit Staked(account, amount);
    }

    function _withdraw(address account, uint256 amount) private {
        if (amount == 0) revert ZeroAmount();
        if (balanceOf[account] < amount) revert InsufficientStake();
        balanceOf[account] -= amount;
        totalStaked -= amount;
        stakingToken.safeTransfer(account, amount);
        emit Withdrawn(account, amount);
    }

    function _claim(address account) private {
        uint256 reward = rewards[account];
        if (reward == 0) return;
        rewards[account] = 0;
        rewardToken.safeTransfer(account, reward);
        emit RewardPaid(account, reward);
    }
}
