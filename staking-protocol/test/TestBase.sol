// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

interface Vm {
    function addr(uint256 privateKey) external returns (address);
    function prank(address sender) external;
    function startPrank(address sender) external;
    function stopPrank() external;
    function warp(uint256 timestamp) external;
    function sign(uint256 privateKey, bytes32 digest) external returns (uint8 v, bytes32 r, bytes32 s);
    function expectRevert(bytes4 selector) external;
    function expectRevert(bytes calldata reason) external;
}

abstract contract TestBase {
    Vm internal constant vm = Vm(address(uint160(uint256(keccak256("hevm cheat code")))));

    function assertEq(uint256 actual, uint256 expected, string memory reason) internal pure {
        require(actual == expected, reason);
    }

    function assertEq(address actual, address expected, string memory reason) internal pure {
        require(actual == expected, reason);
    }

    function assertTrue(bool value, string memory reason) internal pure {
        require(value, reason);
    }

    function assertApproxEqAbs(uint256 actual, uint256 expected, uint256 tolerance, string memory reason)
        internal
        pure
    {
        uint256 difference = actual > expected ? actual - expected : expected - actual;
        require(difference <= tolerance, reason);
    }

    function bound(uint256 value, uint256 minimum, uint256 maximum) internal pure returns (uint256) {
        require(minimum <= maximum, "invalid bounds");
        if (minimum == maximum) return minimum;
        return minimum + (value % (maximum - minimum + 1));
    }
}
