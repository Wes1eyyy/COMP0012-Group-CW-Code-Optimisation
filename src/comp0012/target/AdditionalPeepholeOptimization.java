package comp0012.target;

public class AdditionalPeepholeOptimization {

    public int alwaysTrueBranch() {
        int flag = 1;
        if (flag != 0) {
            return 7;
        }
        return 9;
    }

    public int alwaysFalseBranch() {
        int flag = 0;
        if (flag != 0) {
            return 1;
        }
        return 2;
    }

    public int constantCompareBranch() {
        int a = 3;
        int b = 5;
        if (a < b) {
            return 11;
        }
        return 22;
    }

    public int chainedBranchAfterReassign() {
        int a = 1;
        int out = 0;
        if (a == 1) {
            out = 5;
        } else {
            out = 8;
        }

        a = 2;
        if (a == 1) {
            out = 99;
        }

        return out;
    }
}
