package lah.texpert;

/**
 * ArrayUtils contains some methods that you can call to find out the most efficient increments by which to grow arrays.
 */
public class ArrayUtils {

	private ArrayUtils() { /* cannot be instantiated */
	}

	public static int idealByteArraySize(int need) {
		for (int i = 4; i < 32; i++)
			if (need <= (1 << i) - 12)
				return (1 << i) - 12;

		return need;
	}

	public static int idealCharArraySize(int need) {
		return idealByteArraySize(need * 2) / 2;
	}

	public static int idealIntArraySize(int need) {
		return idealByteArraySize(need * 4) / 4;
	}

	public static int idealObjectArraySize(int need) {
		return idealByteArraySize(need * 4) / 4;
	}

}
