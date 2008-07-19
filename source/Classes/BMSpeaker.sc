BMSpeaker {
	classvar rad2deg;
	var <name; // matches speaker taxonomy we've hashed out
	
	var <index; // SC output
	
	// cartesian
	var <>x, <>y, <>z; // in meters; for 2D arrays z = 0;
	var <>spec; // instance of SpeakerSpec, contains shared info like freq range, etc.
	
	var <>description; // human readable text
	var <>directivity; // symbol, either 'direct' or 'reflected'
	
	// VBAP style spherical coords, angles (probably in degrees) from a central point
	var <>azi; // from median plane +/- 180 deg 
	var <>ele; // above azimuthal plane
	var <>rad; // in meters from (0, 0, 0), which should be audience centre
	
	// dBFS cut populated by auto balncing function. This may be arbitrarily low, 
	// so it should only be used for comparison purposes unless normalised across an array
	var <>autoTrim = 0;
	
	*new {|name, index, x = 1, y = 1, z = 1, spec|
		^super.newCopyArgs(name, index, x, y, z, BMSpeakerSpec.specs[spec.asSymbol]).init;
	}
	
	*initClass { rad2deg = 360.0 / ( 2 * pi );}
	
	init {
		azi = atan2(x, y) * rad2deg;
		rad = (x.squared + y.squared + z.squared).sqrt;
		ele = atan2(z, hypot(x, y)) * rad2deg;
	}
//	*newFromSpherical {|azi, ele, rad = 1|
//		^super.new.initFromSpherical(azi, ele, rad = 1);
//	}
//	
//	initFromSpherical{|azimuth, elevation, radius|
//		azi = azimuth;
//		ele = elevation;
//		rad = radius;
//	}
	
	name_ {|newname| name = newname.asSymbol; } // setter necessary?
	
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
			 // plugins is [[specname, presetname], ...]
			 BMSpeakerSpec('SCM50', (brand: 'ATC', minFreq: 38, maxFreq: 20000, spl: 112, powered: false, plugins: [[\highpass, \atcs]]));
			 BMSpeakerSpec('8030A', (brand: 'Genelec', minFreq: 58, maxFreq: 20000, spl: 97, powered: true));
			 BMSpeakerSpec('8040A', (brand: 'Genelec', minFreq: 48, maxFreq: 20000, spl: 99, powered: true));
			 BMSpeakerSpec('8050A', (brand: 'Genelec', minFreq: 38, maxFreq: 20000, spl: 101, powered: true));
			 BMSpeakerSpec('1037C', (brand: 'Genelec', minFreq: 37, maxFreq: 21000, spl: 107, powered: true));
			 BMSpeakerSpec('1037A', (brand: 'Genelec', minFreq: 39, maxFreq: 21000, spl: 106, powered: true));
			 BMSpeakerSpec('1029A', (brand: 'Genelec', minFreq: 70, maxFreq: 18000, spl: 98, powered: true));
			 BMSpeakerSpec('7070A', (brand: 'Genelec', minFreq: 19, maxFreq: 85, spl: nil, powered: true));
			 BMSpeakerSpec('1094A', (brand: 'Genelec', minFreq: 29, maxFreq: 80, spl: nil, powered: true));
			 BMSpeakerSpec('Circle5', (brand: 'HHb', minFreq: 48, maxFreq: 20000, spl: 87, powered: false));
			 BMSpeakerSpec('Circle3', (brand: 'HHb', minFreq: 70, maxFreq: 20000, spl: 83, powered: false));
			 BMSpeakerSpec('Volt', (brand: 'Wilmslow Audio', minFreq: 35, maxFreq: 30000, spl: 88, powered: false));
			 BMSpeakerSpec('Lynx', (brand: 'Tannoy', minFreq: 50, maxFreq: 20000, spl: 95, powered: false)); // spl assumes two coupled... thanks Tannoy
			 BMSpeakerSpec('MC24', (brand: 'APG', minFreq: 60, maxFreq: 20000, spl: 99, powered: false)); // spl @ 1W / 1 meter
			 // KSN1005 nominal spl 95
			 BMSpeakerSpec('Tweeters', (brand: 'Motorola', minFreq: 10000, maxFreq: 27000, spl: nil, powered: false, plugins: [[\highpass, \tweeters]]));
		 }	
	 }
	 
	// these forward to the appropriate dicts
//	*doesNotUnderstand { arg selector;
//		^if(specs[selector.asSymbol].notNil, {this.newNoInit(selector)}, {super.doesNotUnderstand(selector)});
//	}
	
	doesNotUnderstand { arg selector ... args;
		^this.class.specs[name].perform(selector, *args); // so nil if not there, vals if setter
	}
}

// this is interchangeable with an InOutArray because of 'asUGenInput'
BMSpeakerArray : InOutArray {
	
	add {|speaker|
		super.add(speaker.name -> speaker);
	}
	
	getSubArray {|name| ^subArrays[name].collectAs({|key| this[key]}, this.class); }
	
	isSpeakerArray { ^true } 		
}