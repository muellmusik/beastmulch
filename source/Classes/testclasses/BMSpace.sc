// can we just subtract the speaker radius from all delays?
// phase invert for each reflection?

BMAbstractSpaceModel {

	// could use image receiver model to save on calculations?
	reflections {|x, y, z, order = 1| ^this.subclassResponsibility }	 // this should accept UGens
	
	// maybe be in subclasses?
	rt60 {^this.subclassResponsibility }	
	
	criticalDistance { ^this.subclassResponsibility }
	
}

BM2DBoxRoom : BMAbstractSpaceModel {
	
}


// the first order virtual sources are contained in the virtual rooms having the coordinates (1, 0, 0), (0, 1, 0), (-1, 0, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1). 
// The virtual room coordinates for those second order virtual sources (see FIGS. 4A, 4B, and 4C) are as follows: (1, 0, 1), (0, 1, 1), (-1, 0, 1), (0, -1, 1), (1, 1, 0), (-1, 1, 0), (-1, -1, 0), (1, -1, 0), (1, 0, -1), (0, 1, -1), (-1, 0, -1), (0, -1, -1).

BM3DBoxRoom : BMAbstractSpaceModel {
	
	classvar spm = 0.0034, dpr = 57.29578, tiny = 1.0e-30, dimx = 0, dimy = 1, dimz = 2;
	classvar aZ = 0, eL = 1, delay = 2, scale = 3, refdist = 1;
	classvar map1, map2, map3, ord = #["3rd", "4th"];
	
	var xsize, ysize, zsize, listenerXOffset, listenerYOffset, listenerZOffset;
	
	*new {|xsize, ysize, zsize, listenerXOffset, listenerYOffset, listenerZOffset| 
		^super.newCopyArgs(xsize, ysize, zsize, listenerXOffset, listenerYOffset, listenerZOffset);
	} 
	
	*initClass {
		map1 = [[0,0,1,0,-1,0], [0,1,0,-1,0,0], [1,0,0,0,0,-1]];
		map2 = [
				[0,0,1,0,-1,0,1,2,1,0,-1,-2,-1,0,1,0,-1,0],
				[0,1,0,-1,0,2,1,0,-1,-2,-1,0,1,1,0,-1,0,0],
				[2,1,1,1,1,0,0,0,0,0,0,0,0,-1,-1,-1,-1,-2]
			];
		map3 = [
				[0,0,2,0,-2,0,2,3,2,0,-2,-3,-2,0,2,0,-2,0],
				[0,2,0,-2,0,3,2,0,-2,-3,-2,0,2,2,0,-2,0,0],
				[3,2,2,2,2,0,0,0,0,0,0,0,0,-2,-2,-2,-2,-3]
			];
	}
	
	//maximum source to listener distance
	maxDist { ^sqrt(xsize.squared + ysize.squared + zsize.squared) }

	// listener coords, roomDim, sourceAz, sourceEl, sourceRad
	calcReflections { |az, el, r| 
		var source, first, second, third, fourth, fdelay;
		var x, y, z, sourceX, sourceY, sourceZ, sum, sum2, avg, sd;
		var ix, iy, iz, i, j, k, iord;
		var firstRefs, secondRefs, thirdRefs;
		var spher;
		
		source = FloatArray.newClear(4);
		
		fdelay = FloatArray.newClear(6);
		
		firstRefs = Array.new(6);
		secondRefs = Array.new(18);
		thirdRefs = Array.new(18);
		
		source[aZ] = az;
		source[eL] = el;
		
		// convert meters to seconds
		listenerXOffset = listenerXOffset * spm;
		listenerYOffset = listenerYOffset * spm;
		listenerZOffset = listenerZOffset * spm;
		xsize = xsize * spm;
		ysize = ysize * spm;
		zsize = zsize * spm;
		
		// calc direct then shift origin
		#sourceX, sourceY, sourceZ = this.stoc(az, el, r * spm);
		source[delay] = sqrt(sourceX.squared + sourceY.squared + sourceZ.squared); // direct sound path
		
		// shift origin to room center
		sourceX = sourceX + listenerXOffset;
		sourceY = sourceY + listenerYOffset;
		sourceZ = sourceZ + listenerZOffset;
		
		// calc coords of image model virtual sources
		
		// first order
		6.do({|ir|
			first = FloatArray.newClear(4);
			x = this.cvs(map1[dimx][ir], sourceX, xsize) - listenerXOffset;
			y = this.cvs(map1[dimy][ir], sourceY, ysize) - listenerYOffset;
			z = this.cvs(map1[dimz][ir], sourceZ, zsize) - listenerZOffset;
			spher = this.ctos(x, y, z);
			first[aZ] = spher[0];
			first[eL] = spher[1];
			r = spher[2];
			first[delay] = r - source[delay];
			fdelay[ir] = r;
			first[scale] = source[delay]/(source[delay] + first[delay]);
			firstRefs = firstRefs ++ first; // az, el, delay, scale
		});
		
		// second and higher
		i = 0;
		18.do({|ir|
			second = FloatArray.newClear(4);
			third = FloatArray.newClear(4);
		
			// second
			x = this.cvs(map2[dimx][ir], sourceX, xsize) - listenerXOffset;
			y = this.cvs(map2[dimy][ir], sourceY, ysize) - listenerYOffset;
			z = this.cvs(map2[dimz][ir], sourceZ, zsize) - listenerZOffset;
			spher = this.ctos(x, y, z);
			second[aZ] = spher[0];
			second[eL] = spher[1];
			r = spher[2];
			second[delay] = r - source[delay];
			second[scale] = source[delay]/(source[delay] + second[delay]);
			
			// third +
			x = this.cvs(map3[dimx][ir], sourceX, xsize) - listenerXOffset;
			y = this.cvs(map3[dimy][ir], sourceY, ysize) - listenerYOffset;
			z = this.cvs(map3[dimz][ir], sourceZ, zsize) - listenerZOffset;
			spher = this.ctos(x, y, z);
			third[aZ] = spher[0];
			third[eL] = spher[1];
			r = spher[2];
			third[delay] = r - source[delay] - second[delay];
			third[scale] = (source[delay] + second[delay])/(source[delay] + r);
			iord = abs(map3[dimx][ir]) + abs(map3[dimy][ir]) + abs(map3[dimz][ir]) - 3;
			if(iord == 0, {
				second[delay] = second[delay] - fdelay[i];
				second[scale] = fdelay[i]/(fdelay[i] + second[delay]);
				i = i + 1;
			});
			
			secondRefs = secondRefs ++ second; // az, el, delay, scale
			thirdRefs = thirdRefs ++ third; // az, el, delay, scale
		});
		^[firstRefs, secondRefs, thirdRefs];
	}
	//private
	
	// cartesian to spherical
	ctos { |x, y, z|
		var az, el, r, rad;
		r = sqrt(x.squared + y.squared + z.squared);
		el = asin(z/r) * dpr;
		if(x == 0, {x = tiny});
		rad = atan(y/x);
		if(x > 0, {az = 90 - (rad * dpr)});
		if(x < 0, {az = 270 - (rad * dpr)});
		^[az, el, r];
	}
	
	// spherical to cartesian
	stoc {|az,el, r|
		var x,y,z;
		z = sin(el/dpr) * r;
		r = sqrt(r.squared - z.squared);
		x = cos((90 -az) / dpr) * r;
		y = sin((90 - az) / dpr) * r;
		^[x,y,z]
	}
		
	cvs { |ic,cs, cr|
		//ic = image room coordinate
		//cs = coord of source (rel to room center)
		//cr = room measure on the dimension passed
		//vs = coord of virtual source
		
		var vs;
		
		if(ic == 0, { vs = cs; }, {
			if(abs(ic)%2 != 1, {vs = cs}, {
				vs = cs.neg;
				vs = ic * cr + vs;
			});
		})
		^vs;
	}
}

BMPlaneSurface : BMAbstractSpaceModel {
	
}

// components can be used to efficiently combine
BMEarlyReflections { }

BMDiffuseReverb { }

// this manages multiple sources as a whole
BMSourceModeler { }

// a la Kendall and Mertens
BMSpatialReverberator {
	
	classvar spm = 0.0034;
	
	// source coords relative to listener pos?
	*ar {|input, sourceAzi, sourceEle, sourceDist, room, vbapBuf, numChans, spread = 1, refDist = 1|
		var source, delayedSource, filtered, sourceAtten, refDistRecip, coef = 0.5;
		var firstReflecs, secondReflecs, thirdReflecs;
		var firstRefDel, secondRefDel;
		
		refDistRecip = 1 / refDist;
		
		#firstReflecs, secondReflecs, thirdReflecs = room.calcReflections(sourceAzi, sourceEle, sourceDist); 
		
		sourceAtten = (sourceDist * refDistRecip).reciprocal;
		delayedSource = BufRdDelay(input * sourceAtten, room.maxDist * spm, sourceDist * spm);
		
		// could add distance filtering here
		source = VBAP.ar(numChans, delayedSource, vbapBuf, sourceAzi, sourceEle, spread);
		
		filtered = OnePole.ar(input, coef);
		// az, el, delay, scale
		firstRefDel = MultiBufRdDelay.ar(filtered, room.maxDist * spm * 2, firstReflecs.flop[2]);
		//firstReflecs = 
		 
	}
	
}

// pseudo Ugen for audio rate interp
BufRdDelay {
	
	*ar {|in, maxDelayTime, delayTime|
		var buf, phasor, maxFrames, sr, out;
		sr = SampleRate.ir;
		maxFrames = maxDelayTime * sr;
		buf = LocalBuf(maxFrames, 1);
		phasor = Phasor.ar(0, 1, 0, maxFrames);
		out = BufRd.ar(1, buf, phasor + (delayTime * sr) - (ControlDur.ir * sr) % maxFrames, 1, 2);
		BufWr.ar(in, buf, phasor, 1);
		^out
	}
}

MultiBufRdDelay {
	
	*ar {|in, maxDelayTime, delayTimes|
		var buf, phasor, maxFrames, sr, cd, out;
		sr = SampleRate.ir;
		maxFrames = maxDelayTime * sr;
		buf = LocalBuf(maxFrames, 1);
		phasor = Phasor.ar(0, 1, 0, maxFrames);
		cd = ControlDur.ir;
		out = delayTimes.collect({|delayTime|
			BufRd.ar(1, buf, phasor + (delayTime * sr) - (cd * sr) % maxFrames, 1, 2);
		});
		BufWr.ar(in, buf, phasor, 1);
		^out
	}
}
	

// Kendall-Mertens comb units
R1 {
	
	*ar{|in, maxDelayTime, delayTime, coef, fbScale = 0.9|
		var buf, phasor, maxFrames, sr, ff, out;
		sr = SampleRate.ir;
		maxFrames = maxDelayTime * sr;
		buf = LocalBuf(maxFrames, 1);
		phasor = Phasor.ar(0, 1, 0, maxFrames);
		ff = BufRd.ar(1, buf, phasor + (delayTime * sr) - (ControlDur.ir * sr) % maxFrames, 1, 2);
		ff = OnePole.ar(ff);
		out = in + ff;
		BufWr.ar(in + (ff * fbScale), buf, phasor, 1);
		^out
	}
}

R2 {
	
	*ar{|in, maxDelayTime1, delayTime1, maxDelayTime2, delayTime2, coef, fbScale = 0.9|
		var buf1, phasor1, maxFrames1, sr, ff1, out;
		var buf2, phasor2, maxFrames2, ff2;
		sr = SampleRate.ir;
		
		// delay1 params
		maxFrames1 = maxDelayTime1 * sr;
		buf1 = LocalBuf(maxFrames1, 1);
		phasor1 = Phasor.ar(0, 1, 0, maxFrames1);
		
		// delay2 params
		maxFrames2 = maxDelayTime2 * sr;
		buf2 = LocalBuf(maxFrames2, 1);
		phasor2 = Phasor.ar(0, 1, 0, maxFrames2);
		
		// get and filter output of delay1
		ff1 = BufRd.ar(1, buf1, phasor1 + (delayTime1 * sr) - (ControlDur.ir * sr) % maxFrames1, 1, 2);
		ff1 = OnePole.ar(ff1);
		
		// write ff1 to delay2
		BufWr.ar(ff1, buf2, phasor2, 1);
		
		// get and filter output of delay2
		ff2 = BufRd.ar(1, buf2, phasor2 + (delayTime2 * sr) - (ControlDur.ir * sr) % maxFrames2, 1, 2);
		ff2 = OnePole.ar(ff2);
		
		out = in + ff1 + ff2;
		// feedback into delay1
		BufWr.ar(in + (ff2 * fbScale), buf1, phasor1, 1);
		
		^out
	}
}