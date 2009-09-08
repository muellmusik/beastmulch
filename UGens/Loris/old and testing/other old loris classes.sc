LP4Noise : UGen {
	
	*ar { arg mul = 1.0, add = 0.0;
		// support this idiom from SC2.
		if (mul.isArray, {
			^{ this.multiNew('audio') }.dup(mul.size).madd(mul, add)
		},{
			^this.multiNew('audio').madd(mul, add)
		});
	}
	*kr { arg mul = 1.0, add = 0.0;
		if (mul.isArray, {
			^{ this.multiNew('control') }.dup(mul.size).madd(mul, add)
		},{
			^this.multiNew('control').madd(mul, add)
		});
	}
	
}

LorisMod : UGen {
	
	*ar { arg bw = 0.0, mul = 1.0, add = 0.0;
		// support this idiom from SC2.
		if (mul.isArray, {
			^{ this.multiNew('audio', bw) }.dup(mul.size).madd(mul, add)
		},{
			^this.multiNew('audio', bw).madd(mul, add)
		});
	}
	
}
