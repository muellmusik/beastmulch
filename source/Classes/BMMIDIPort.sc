BMMIDIPort {
	classvar <ports;
	var <name, <inuid, <outuid, <device, <outport;
	
	*initClass {
		ports = IdentityDictionary.new;
	}
	
	*init {
		if(MIDIClient.initialized.not,{ MIDIIn.connectAll });
		MIDIClient.sources.do({ |source| 
			var name, dest, destuid;
			name = source.name;
			dest = MIDIOut.findPort(source.device, source.name);
			dest.notNil.if({ destuid = dest.uid; });
			this.new(name, source.uid, destuid, source.device, MIDIClient.destinations.indexOf(dest));
		});
		// sometimes only an out
		MIDIClient.destinations.do({ |dst, i|
			var name;
			name = dst.name;
			if(ports[name.asSymbol].isNil {
				this.new(name, nil, dst.uid, dst.device, i);
			});
		}); 
	}
	
	*new {|name, inuid, outuid, device, outport|
		^super.newCopyArgs(name.asSymbol, inuid, outuid, device, outport).init;
	}
	
	init {
		ports[name] = this;
	}

}