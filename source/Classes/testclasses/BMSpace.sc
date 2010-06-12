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
		
		source = FloatArray.newClear(4);
		first = FloatArray.newClear(4);
		second = FloatArray.newClear(4);
		third = FloatArray.newClear(4);
		fdelay = FloatArray.newClear(6);
		
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
		#xs, ys, zs = stoc(az, el, r * spm);
		source[delay] = sqrt(xs.squared + ys.squared + zs.squared); // direct sound path
		
		// shift origin to room center
		xs = xs + xl;
		ys = ys + yl;
		zs = zs + zl;
		
		// calc coords of image model virtual sources
		5.do({|ir|
			x = cvs(map1[dimx][ir], xs, xr) - xl;
			y = cvs(map1[dimx][ir], ys, yr) - yl;
			z = cvs(map1[dimx][ir], zs, zr) - zl;
			
		});
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