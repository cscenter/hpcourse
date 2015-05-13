package ru.compscicenter2015.concurrency;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class MergeSortClass implements Runnable {
	private MyFixedThreadPool pool;
	private int array[];
	private int l;
	private int r;
	
	MergeSortClass(int array[], int l, int r) {
		this.array = array;
		this.l = l;
		this.r = r;
		pool = new MyFixedThreadPool(4);
	}
	
	public int[] getArray() {
		return array;
	}
	
	public void merge(int l, int r) {
		int tempArray[] = new int[r - l + 1];
		int posInLeftPart = l;
		int middle = (l + r) / 2;
		int posInRightPart = middle + 1;
		for (int posInTempArray = 0; posInTempArray < tempArray.length; posInTempArray++) {
			if (posInRightPart > r
					|| (posInLeftPart <= middle && array[posInLeftPart] <= array[posInRightPart])) {
				tempArray[posInTempArray] = array[posInLeftPart++];
			} else {
				tempArray[posInTempArray] = array[posInRightPart++];
			}
		}
		for (int i = l; i <= r; i++)
			array[i] = tempArray[i - l];
	}
	
	@Override
	public void run() {
		if (r - l <= 0)
			return;
		if (r - l + 1 <= 4) {
			for (int i = l; i < r; i++)
				for (int j = i + 1; j <= r; j++)
					if (array[i] > array[j]) {
						int temp = array[i];
						array[i] = array[j];
						array[j] = temp;
					}
		} else {
			int m = (r + l) / 2;
			Future<?> futureForLeft = pool.submit(new MergeSortClass(array, l,
					m));
			Future<?> futureForRight = pool.submit(new MergeSortClass(array,
					m + 1, r));
			try {
				futureForLeft.get();
			} catch (InterruptedException e) {
				// Значит надо поставить статус канселед

			} catch (ExecutionException e) {
				// Поставить error
			}
			try {
				futureForRight.get();
			} catch (InterruptedException e) {
				// Значит надо поставить статус канселед

			} catch (ExecutionException e) {
				// Поставить error
			}
		}
		merge(l, r);
	}
	
}
