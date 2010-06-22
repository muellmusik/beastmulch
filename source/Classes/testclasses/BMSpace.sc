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
	
	var xsize, ysize, zsize, listenerXOffset;
	
	*new {|xsize, ysize, zsize, listenerXOffset| 
		^super.newCopyArgs
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
	
//	reflections { |x,y,z, order|
//		
//		
//	}

	// listener coords, roomDim, sourceAz, sourceEl, sourceRad
	calcReflections { |xl, yl, zl, xr, yr, zr, az, el, r| 
		var source, first, second, third, fourth, fdelay;
		var x, y, z, xs, ys, zs, sum, sum2, avg, sd;
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
		xl = xl * spm;
		yl = yl * spm;
		zl = zl * spm;
		xr = xr * spm;
		yr = yr * spm;
		zr = zr * spm;
		
		// calc direct then shift origin
		#xs, ys, zs = this.stoc(az, el, r * spm);
		source[delay] = sqrt(xs.squared + ys.squared + zs.squared); // direct sound path
		
		// shift origin to room center
		xs = xs + xl;
		ys = ys + yl;
		zs = zs + zl;
		
		// calc coords of image model virtual sources
		
		// first order
		6.do({|ir|
			first = FloatArray.newClear(4);
			x = this.cvs(map1[dimx][ir], xs, xr) - xl;
			y = this.cvs(map1[dimy][ir], ys, yr) - yl;
			z = this.cvs(map1[dimz][ir], zs, zr) - zl;
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
			x = this.cvs(map2[dimx][ir], xs, xr) - xl;
			y = this.cvs(map2[dimy][ir], ys, yr) - yl;
			z = this.cvs(map2[dimz][ir], zs, zr) - zl;
			spher = this.ctos(x, y, z);
			second[aZ] = spher[0];
			second[eL] = spher[1];
			r = spher[2];
			second[delay] = r - source[delay];
			second[scale] = source[delay]/(source[delay] + second[delay]);
			
			// third +
			x = this.cvs(map3[dimx][ir], xs, xr) - xl;
			y = this.cvs(map3[dimy][ir], ys, yr) - yl;
			z = this.cvs(map3[dimz][ir], zs, zr) - zl;
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