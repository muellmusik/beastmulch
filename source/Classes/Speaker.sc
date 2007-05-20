Speaker {
	var <>label; // matches speaker taxonomy we've hashed out
	var <>locationDescription; // human readable text?
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
	
	// debatable if we need this?
	var <>channelNumber; // SC output
	
	*newFromSpherical {|azi, ele, rad = 1|
		^super.new.initFromSpherical(azi, ele, rad = 1);
	}
	
	initFromSpherical{|azimuth, elevation, radius|
		azi = azimuth;
		ele = elevation;
		rad = radius;
	}
}