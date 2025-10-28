package common;

import java.math.BigInteger;
import java.util.Scanner;

public class alg {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        long n_long = 100000;
        long k = 100000;
        BigInteger n = BigInteger.valueOf(n_long);
        long steps = 0;

        BigInteger ZERO = BigInteger.ZERO;
        BigInteger ONE = BigInteger.ONE;
        BigInteger TWO = BigInteger.valueOf(2);
        BigInteger THREE = BigInteger.valueOf(3);
        BigInteger FOUR = BigInteger.valueOf(4);

        while (steps < k) {
            if (n.equals(ONE) || n.equals(TWO) || n.equals(FOUR)) {
                long remaining = k - steps;
                long r = remaining % 3;
                for (long i = 0; i < r; i++) {
                    if (n.mod(TWO).equals(ZERO)) {
                        n = n.divide(TWO);
                    } else {
                        n = n.multiply(THREE).add(ONE);
                    }
                }
                break;
            }
            if (n.mod(TWO).equals(ZERO)) {
                int tz = n.getLowestSetBit();
                long add = tz;
                long rem = k - steps;
                if (add > rem) {
                    add = rem;
                }
                n = n.shiftRight((int) add);
                steps += add;
            } else {
                n = n.multiply(THREE).add(ONE);
                steps++;
            }
        }
        System.out.print(n);
    }
}
