+ Signal {
	
	crosscorrelate {|testSignal, norm = false|
		var testSize, result;
		testSize = testSignal.size;
		//testThis = this.extend(this.size + testSize -1, 0);
		result = Signal.fill(this.size, {|i|
			(this.copyToEnd(i).extend(testSize, 0) * testSignal).sum;
		});
		norm.if({result = result / Signal.fill(1, {this.squared.sum})[0]});
		^result
	}
	
	
	// need to consider if this is really extending properly
	crosscorrelateFD {|testSignal, norm = false|
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
		result = ifft(result.real, result.imag, cosTable).real;
		norm.if({result = result / Signal.fill(1, {this.squared.sum})[0]});
		^result
	}
}