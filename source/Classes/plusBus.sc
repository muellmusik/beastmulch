+ Bus {

	asBMInOutArray {|name|
		^numChannels.collectAs({|channum| 
			(name.asString ++ "-" ++ (channum + 1)).asSymbol->(index + channum)
		 }, BMInOutArray)
	}
} 