+ Signal {
	
	crosscorrelate {|testSignal|
		var testThis, testSize;
		testSize = testSignal.size;
		testThis = this.extend(this.size + testSize -1, 0);
		^Signal.fill(this.size, {|i|
			(testThis.copyRange(i, i + testSize - 1) * testSignal).sum;
		});
	}
	
	crosscorrelateFD {|testSignal|
		var zeroPadded, fftThis, fftThat, size, cosTable, imagThis, imagThat, result;
		
		zeroPadded = this.extend((this.size + testSignal.size -1).nextPowerOfTwo);
		size = zeroPadded.size;
		cosTable = Signal.fftCosTable(size);
		imagThis = Signal.newClear(size);
		imagThat = Signal.newClear(size);
		fftThis = fft(zeroPadded, imagThis, cosTable);
		testSignal = testSignal.extend(size, 0);
		fftThat = fft(testSignal, imagThat, cosTable).conjugate;
		result = fftThis * fftThat;
		^ifft(result.real, result.imag, cosTable).real 
		
	}
}