package comp0012.target;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test additional peephole optimisation targets.
 */
public class AdditionalPeepholeOptimizationTest {

    AdditionalPeepholeOptimization apo = new AdditionalPeepholeOptimization();

    @Test
    public void testAlwaysTrueBranch() {
        assertEquals(7, apo.alwaysTrueBranch());
    }

    @Test
    public void testAlwaysFalseBranch() {
        assertEquals(2, apo.alwaysFalseBranch());
    }

    @Test
    public void testConstantCompareBranch() {
        assertEquals(11, apo.constantCompareBranch());
    }

    @Test
    public void testChainedBranchAfterReassign() {
        assertEquals(5, apo.chainedBranchAfterReassign());
    }
}
