Speaker {
	var <name; // matches speaker taxonomy we've hashed out
	var <>description; // human readable text
	var <>directivity; // symbol, either 'direct' or 'reflected'
	var <>spec; // instance of SpeakerSpec, contains shared info like freq range, etc.
	
	// cartesian
	var <>x, <>y, <>z;
	
	// vbap specific cartesian
	// we will probably want our own cartesian coords, as vbap uses these in a idiosyncratic way
	// so duplicate x, y, and z here
	var <>vbapx, <>vbapy, <>vbapz;
	
	// spherical coords, angles (probably in degrees) from a central point
	var <>azi; // from median plane +/- 180 deg 
	var <>ele; // above azimuthal plane
	var <>rad;	// in meters?
	
	var <>index; // SC output
	
	*newFromSpherical {|azi, ele, rad = 1|
		^super.new.initFromSpherical(azi, ele, rad = 1);
	}
	
	initFromSpherical{|azimuth, elevation, radius|
		azi = azimuth;
		ele = elevation;
		rad = radius;
	}
	
	name_ {|newname| name = newname.asSymbol; }
	
	asUGenInput { ^index }
}

// Wrapper class for managing specs for different speaker models
// speakerspecs are pseudo-singletons: There can only be one of each name

// the only required field is 'name' so any use of these should deal appropriately with nil values
BMSpeakerSpec {
	
	classvar <specs;
	var <name;

	*new { | name, vals | // vals is an event or other Dictionary subclass
		^super.newCopyArgs(name.asSymbol).init(vals.as(Event));
	}
	
	*newNoInit { |name|
		^super.newCopyArgs(name.asSymbol)
	}
	 
	init { |vals|
		this.class.specs[this.name] = vals;
	}
	
	*initClass {
		 StartUp.add{ 
			 specs = ();
			 
			 // spl is continuous at 1m
			 BMSpeakerSpec('SCM50', (brand: 'ATC', minFreq: 38, maxFreq: 20000, spl: 112, powered: false));
			 BMSpeakerSpec('8030A', (brand: 'Genelec', minFreq: 58, maxFreq: 20000, spl: 97, powered: true));
			 BMSpeakerSpec('8040A', (brand: 'Genelec', minFreq: 48, maxFreq: 20000, spl: 99, powered: true));
			 BMSpeakerSpec('8050A', (brand: 'Genelec', minFreq: 38, maxFreq: 20000, spl: 101, powered: true));
			 BMSpeakerSpec('1037C', (brand: 'Genelec', minFreq: 37, maxFreq: 21000, spl: 107, powered: true));
			 BMSpeakerSpec('1037A', (brand: 'Genelec', minFreq: 39, maxFreq: 21000, spl: 106, powered: true));
			 BMSpeakerSpec('1029A', (brand: 'Genelec', minFreq: 70, maxFreq: 18000, spl: 98, powered: true));
			 BMSpeakerSpec('7070A', (brand: 'Genelec', minFreq: 19, maxFreq: 85, spl: nil, powered: true));
			 BMSpeakerSpec('1094A', (brand: 'Genelec', minFreq: 29, maxFreq: 80, spl: nil, powered: true));
			 BMSpeakerSpec('MC24', (brand: 'APG', minFreq: 60, maxFreq: 20000, spl: 99, powered: false)); // spl @ 1W / 1 meter
		 }	
	 }
	 
	// these forward to the appropriate dicts
	*doesNotUnderstand { arg selector;
		^if(specs[selector.asSymbol].notNil, {this.newNoInit(selector)}, {super.doesNotUnderstand(selector)});
	}
	
	doesNotUnderstand { arg selector ... args;
		^this.class.specs[name].perform(selector, *args); // so nil if not there, vals if setter
	}
}

// this is interchangeable with an InOutArray because of 'asUGenInput'
BMSpeakerArray : InOutArray {
	
	add {|speaker|
		super.add(speaker.name -> speaker);
	} 		
}