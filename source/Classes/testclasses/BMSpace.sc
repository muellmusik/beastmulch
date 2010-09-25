// can we just subtract the speaker radius from all delays?
// phase invert for each reflection?

BMAbstractSpaceModel {

	// could use image receiver model to save on calculations?
	reflections {|x, y, z, order = 1| ^this.subclassResponsibility }	 // this should accept UGens
	
	// maybe be in subclasses?
	rt60 {^this.subclassResponsibility }	
	
	criticalDistance { ^this.subclassResponsibility }
	
}

BMAbstractRoom : BMAbstractSpaceModel {
	classvar spm = 0.0034, dpr, rpd, tiny = 1.0e-30, dimx = 0, dimy = 1, dimz = 2;
	classvar aZ = 0, eL = 1, delay = 2, scale = 3, refdist = 1;
	
	var xsize, ysize, zsize, listenerXOffset, listenerYOffset, listenerZOffset;
	
	*initClass { 
		dpr = 360.0 / ( 2 * pi );
		rpd = (2 * pi / 360);	
	}
}

BM2DBoxRoom : BMAbstractRoom {
	
	
	classvar map1, map2, map3;
	
	*new {|xsize, ysize, listenerXOffset = 0, listenerYOffset = 0| 
		// convert meters to seconds
		^super.newCopyArgs(xsize * spm, ysize * spm, 0, listenerXOffset  * spm, listenerYOffset * spm, 0);
	} 
	
	*initClass {
		// vertical commented out
		map1 = [ 
			//[ 0, 0, 1 ], 
			[ 0, 1, 0 ], 
			[ 1, 0, 0 ], 
			[ 0, -1, 0 ], 
			[ -1, 0, 0 ], 
			//[ 0, 0, -1 ] 
		].flop;
		
		map2 = [ 
			//[ 0, 0, 2 ], 
			//[ 0, 1, 1 ], 
			//[ 1, 0, 1 ], 
			//[ 0, -1, 1 ], 
			//[ -1, 0, 1 ], 
			[ 0, 2, 0 ], 
			[ 1, 1, 0 ], 
			[ 2, 0, 0 ], 
			[ 1, -1, 0 ], 
			[ 0, -2, 0 ], 
			[ -1, -1, 0 ], 
			[ -2, 0, 0 ], 
			[ -1, 1, 0 ], 
			//[ 0, 1, -1 ], 
			//[ 1, 0, -1 ], 
			//[ 0, -1, -1 ], 
			//[ -1, 0, -1 ], 
			//[ 0, 0, -2 ]
		].flop;
		
		map3 = [ 
//			[ 0, 0, 3 ], 
//			[ 0, 2, 2 ], 
//			[ 2, 0, 2 ], 
//			[ 0, -2, 2 ], 
//			[ -2, 0, 2 ], 
			[ 0, 3, 0 ], 
			[ 2, 2, 0 ], 
			[ 3, 0, 0 ], 
			[ 2, -2, 0 ], 
			[ 0, -3, 0 ], 
			[ -2, -2, 0 ], 
			[ -3, 0, 0 ], 
			[ -2, 2, 0 ], 
//			[ 0, 2, -2 ], 
//			[ 2, 0, -2 ], 
//			[ 0, -2, -2 ], 
//			[ -2, 0, -2 ], 
//			[ 0, 0, -3 ] 
		].flop;
	}
	
	r1DelIndices { 
		var flop; 
		flop = map3.flop;
		^[[2, 2, 0], [-2, 2, 0], [-2, -2, 0], [2, -2, 0],
			 //[0, 2, 2], [-2, 0, 2], [0, -2, 2], [2, 0, 2], [0, 2, -2], [-2, 0, -2], [0, -2, -2], [2, 0, -2]
			 ]
			.collect({|room|
			 	flop.indexOfEqual(room);
			});
	}
	
	// awkward but safe
	r2DelOneIndices { 
		var flop; 
		flop = map2.flop;
		^map1.flop.collect({|room| room.collect({|coord| if(abs(coord) == 1, {coord + coord}, {coord}); }) })
			.collect({|room|
			 	flop.indexOfEqual(room);
			});
	}
	///******** here is the problem
	crossFeedIndices {
		var flop, r2DelOneIndices;
		r2DelOneIndices = this.r2DelOneIndices; 
		
		"r2DelOneIndices: %\n".postf(r2DelOneIndices);
		flop = map2.flop.reject({|item, i| r2DelOneIndices.indexOf(i).notNil });
		^map1.flop.collect({|room|
			var rooms, one, a, b;
			one = room.abs.indexOf(1);
			// keep 'one' the same but get all permutations of the other two such that if a is zero b.abs = 1
			rooms = all {: [a, b].insert(one, room[one]), a <-(-1..1), b <-(-1..1), (a + b).abs == 1 };
			rooms.collect({|cfRoom| flop.indexOfEqual(cfRoom) });
		});	
	}
	
	//maximum source to listener delay
	maxDelay { ^sqrt(xsize.squared + ysize.squared) }

	// listener coords, roomDim, sourceAz, sourceRad
	calcReflections { |az, r| 
		var source, first, second, third, fourth, fdelay;
		var x, y, sourceX, sourceY, sum, sum2, avg, sd;
		var ix, iy, i, j, k, iord;
		var firstRefs, secondRefs, thirdRefs;
		var polar;
		var ord = #["3rd:", "4th:"];
		
		source = Array.newClear(4);
		
		fdelay = Array.newClear(6);
		
		firstRefs = Array.new(6);
		secondRefs = Array.new(18);
		thirdRefs = Array.new(18);
		
		source[aZ] = az;
		//source[eL] = el;
		
		// convert meters to seconds
		// moved above to avoid repeatedly doing this
//		listenerXOffset = listenerXOffset * spm;
//		listenerYOffset = listenerYOffset * spm;
//		listenerZOffset = listenerZOffset * spm;
//		xsize = xsize * spm;
//		ysize = ysize * spm;
//		zsize = zsize * spm;
		
		// calc direct then shift origin
		#sourceX, sourceY = this.ptoc(az, r * spm);
		source[delay] = sqrt(sourceX.squared + sourceY.squared); // direct sound path
		
		"source: %, %, %\n".postf(source[aZ], source[delay], refdist / r);
		
		// shift origin to room center
		sourceX = sourceX + listenerXOffset;
		sourceY = sourceY + listenerYOffset;
		
		// calc coords of image model virtual sources
		"ix	iy	iz	order	az	delay	scale".postln;
		
		
		// first order
		4.do({|ir|
			first = Array.newClear(4);
			x = this.cvs(map1[dimx][ir], sourceX, xsize) - listenerXOffset;
			y = this.cvs(map1[dimy][ir], sourceY, ysize) - listenerYOffset;
			polar = this.ctop(x, y);
			first[aZ] = polar[0];
			//first[eL] = spher[1];
			first[eL] = 0;
			r = polar[1];
			first[delay] = r - source[delay];
			fdelay[ir] = r;
			first[scale] = source[delay]/(source[delay] + first[delay]);
			firstRefs = firstRefs.add(first); // az, el(0) , delay, scale
			
			"%	%	".postf(map1[dimx][ir], map1[dimy][ir]);
			"1st:		%	%	%\n".postf(first[aZ], first[delay], first[scale]);
		});
		
		"second".postln;
		
		// second and higher
		i = 0;
		8.do({|ir|
			second = Array.newClear(4);
			third = Array.newClear(4);
		
			// second
			x = this.cvs(map2[dimx][ir], sourceX, xsize) - listenerXOffset;
			y = this.cvs(map2[dimy][ir], sourceY, ysize) - listenerYOffset;
			polar = this.ctop(x, y);
			second[aZ] = polar[0];
			//second[eL] = spher[1];
			second[eL] = 0;
			r = polar[1];
			//"spher: %\n".postf(spher);
			second[delay] = r - source[delay];
			//"second[delay]: %\n".postf(second[delay]);
			second[scale] = source[delay]/(source[delay] + second[delay]);
			//"second[scale]: %\n".postf(second[scale]);
			
			"%	%	".postf(map2[dimx][ir], map2[dimy][ir]);
			
			// third +
			x = this.cvs(map3[dimx][ir], sourceX, xsize) - listenerXOffset;
			y = this.cvs(map3[dimy][ir], sourceY, ysize) - listenerYOffset;
			polar = this.ctop(x, y);
			third[aZ] = polar[0];
			//third[eL] = spher[1];
			third[eL] = 0;
			r = polar[1];
			third[delay] = r - source[delay] - second[delay];
			third[scale] = (source[delay] + second[delay])/(source[delay] + r);
			iord = abs(map3[dimx][ir]) + abs(map3[dimy][ir]) - 2;
			// infinities happen in second[scale] here
			if(iord == 0, {
				//"fdelay[i]: %\n".postf(fdelay[i]);
				second[delay] = second[delay] - fdelay[i];
				//"second[delay]: %\n".postf(second[delay]);
				second[scale] = fdelay[i]/(fdelay[i] + second[delay]);
				i = i + 1;
			});
			//"second[scale]: %\n".postf(second[scale]);
			secondRefs = secondRefs.add(second); // az, el(0), delay, scale
			thirdRefs = thirdRefs.add(third); // az, el(0), delay, scale
			
			"2nd:		%	%	%\n".postf(second[aZ], second[delay], second[scale]);
			"%	%	".postf(map3[dimx][ir], map3[dimy][ir]);
			"%				%	%\n".postf(ord[iord], third[delay], third[scale]);
		});
		^[firstRefs, secondRefs, thirdRefs];
	}
	//private
	
	// cartesian to polar
	ctop { |x, y|
		var az, r, rad, offset;
		
		r = sqrt(x.squared + y.squared);
		//if(x == 0, {x = tiny});
		x = if(x.abs > 0, x, tiny); // no divide by 0
		
		rad = atan(y/x);
		//if(x > 0, {az = 90 - (rad * dpr)});
//		if(x < 0, {az = 270 - (rad * dpr)});
		
		offset = if(x > 0, 90, 270);
		az = offset - (rad * dpr);
		^[az, r];
	}
	
	// polar to cartesian
	ptoc {|az, r|
		var x,y;
		x = cos((90 -az) * rpd) * r;
		y = sin((90 - az) * rpd) * r;
		^[x, y]
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
			});
			vs = ic * cr + vs;
		})
		^vs;
	}

	
}


// the first order virtual sources are contained in the virtual rooms having the coordinates (1, 0, 0), (0, 1, 0), (-1, 0, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1). 
// The virtual room coordinates for those second order virtual sources (see FIGS. 4A, 4B, and 4C) are as follows: (1, 0, 1), (0, 1, 1), (-1, 0, 1), (0, -1, 1), (1, 1, 0), (-1, 1, 0), (-1, -1, 0), (1, -1, 0), (1, 0, -1), (0, 1, -1), (-1, 0, -1), (0, -1, -1).

BM3DBoxRoom : BMAbstractRoom {
	
	classvar map1, map2, map3;
	
	*new {|xsize, ysize, zsize, listenerXOffset = 0, listenerYOffset = 0, listenerZOffset = 0| 
		// convert meters to seconds
		^super.newCopyArgs(xsize * spm, ysize * spm, zsize * spm, listenerXOffset  * spm, listenerYOffset * spm, listenerZOffset * spm);
	} 
	
	*initClass {
		map1 = [ 
			[ 0, 0, 1 ], 
			[ 0, 1, 0 ], 
			[ 1, 0, 0 ], 
			[ 0, -1, 0 ], 
			[ -1, 0, 0 ], 
			[ 0, 0, -1 ] 
		].flop;
		
		map2 = [ 
			[ 0, 0, 2 ], 
			[ 0, 1, 1 ], 
			[ 1, 0, 1 ], 
			[ 0, -1, 1 ], 
			[ -1, 0, 1 ], 
			[ 0, 2, 0 ], 
			[ 1, 1, 0 ], 
			[ 2, 0, 0 ], 
			[ 1, -1, 0 ], 
			[ 0, -2, 0 ], 
			[ -1, -1, 0 ], 
			[ -2, 0, 0 ], 
			[ -1, 1, 0 ], 
			[ 0, 1, -1 ], 
			[ 1, 0, -1 ], 
			[ 0, -1, -1 ], 
			[ -1, 0, -1 ], 
			[ 0, 0, -2 ]
		].flop;
		
		map3 = [ 
			[ 0, 0, 3 ], 
			[ 0, 2, 2 ], 
			[ 2, 0, 2 ], 
			[ 0, -2, 2 ], 
			[ -2, 0, 2 ], 
			[ 0, 3, 0 ], 
			[ 2, 2, 0 ], 
			[ 3, 0, 0 ], 
			[ 2, -2, 0 ], 
			[ 0, -3, 0 ], 
			[ -2, -2, 0 ], 
			[ -3, 0, 0 ], 
			[ -2, 2, 0 ], 
			[ 0, 2, -2 ], 
			[ 2, 0, -2 ], 
			[ 0, -2, -2 ], 
			[ -2, 0, -2 ], 
			[ 0, 0, -3 ] 
		].flop;
	}
	
	r1DelIndices { 
		var flop; 
		flop = map3.flop;
		^[[2, 2, 0], [-2, 2, 0], [-2, -2, 0], [2, -2, 0], [0, 2, 2], [-2, 0, 2], [0, -2, 2], [2, 0, 2], [0, 2, -2], [-2, 0, -2], [0, -2, -2], [2, 0, -2]]
			.collect({|room|
			 	flop.indexOfEqual(room);
			});
	}
	
	// awkward but safe
	r2DelOneIndices { 
		var flop; 
		flop = map2.flop;
		^map1.flop.collect({|room| room.collect({|coord| if(abs(coord) == 1, {coord + coord}, {coord}); }) })
			.collect({|room|
			 	flop.indexOfEqual(room);
			});
	}
	
	crossFeedIndices {
		var flop, r2DelOneIndices;
		r2DelOneIndices = this.r2DelOneIndices; 
		flop = map2.flop.reject({|item, i| r2DelOneIndices.indexOf(i).notNil });
		^map1.flop.collect({|room|
			var rooms, one, a, b;
			one = room.abs.indexOf(1);
			// keep 'one' the same but get all permutations of the other two such that if a is zero b.abs = 1
			rooms = all {: [a, b].insert(one, room[one]), a <-(-1..1), b <-(-1..1), (a + b).abs == 1 };
			rooms.collect({|cfRoom| flop.indexOfEqual(cfRoom) });
		});	
	}
	
	//maximum source to listener delay
	maxDelay { ^sqrt(xsize.squared + ysize.squared + zsize.squared) }
	
	roomSymbol {|x, y, z| ^(x.asString ++ y.asString ++ z.asString).asSymbol } 

	// use Dict version below...
	// listener coords, roomDim, sourceAz, sourceEl, sourceRad
//	calcReflections { |az, el, r| 
//		var source, first, second, third, fourth, fdelay;
//		var x, y, z, sourceX, sourceY, sourceZ, sum, sum2, avg, sd;
//		var ix, iy, iz, i, j, k, iord;
//		var firstRefs, secondRefs, thirdRefs;
//		var spher;
//		var ord = #["3rd:", "4th:"];
//		
//		source = Array.newClear(4);
//		
//		fdelay = Array.newClear(6);
//		
//		firstRefs = Array.new(6);
//		secondRefs = Array.new(18);
//		thirdRefs = Array.new(18);
//		
//		source[aZ] = az;
//		source[eL] = el;
//		
//		// convert meters to seconds
//		// moved above to avoid repeatedly doing this
////		listenerXOffset = listenerXOffset * spm;
////		listenerYOffset = listenerYOffset * spm;
////		listenerZOffset = listenerZOffset * spm;
////		xsize = xsize * spm;
////		ysize = ysize * spm;
////		zsize = zsize * spm;
//		
//		// calc direct then shift origin
//		#sourceX, sourceY, sourceZ = this.stoc(az, el, r * spm);
//		source[delay] = sqrt(sourceX.squared + sourceY.squared + sourceZ.squared); // direct sound path
//		
//		"source: %, %, %, %\n".postf(source[aZ], source[eL], source[delay], refdist / r);
//		
//		// shift origin to room center
//		sourceX = sourceX + listenerXOffset;
//		sourceY = sourceY + listenerYOffset;
//		sourceZ = sourceZ + listenerZOffset;
//		
//		// calc coords of image model virtual sources
//		"ix	iy	iz	order	az	el				delay				scale".postln;
//		
//		
//		// first order
//		6.do({|ir|
//			first = Array.newClear(4);
//			x = this.cvs(map1[dimx][ir], sourceX, xsize) - listenerXOffset;
//			y = this.cvs(map1[dimy][ir], sourceY, ysize) - listenerYOffset;
//			z = this.cvs(map1[dimz][ir], sourceZ, zsize) - listenerZOffset;
//			spher = this.ctos(x, y, z);
//			first[aZ] = spher[0];
//			first[eL] = spher[1];
//			r = spher[2];
//			first[delay] = r - source[delay];
//			fdelay[ir] = r;
//			first[scale] = source[delay]/(source[delay] + first[delay]);
//			firstRefs = firstRefs.add(first); // az, el, delay, scale
//			
//			"%	%	%	".postf(map1[dimx][ir], map1[dimy][ir], map1[dimz][ir]);
//			"1st:		%	%	%	%\n".postf(first[aZ], first[eL], first[delay], first[scale]);
//		});
//		
//		// second and higher
//		i = 0;
//		18.do({|ir|
//			second = Array.newClear(4);
//			third = Array.newClear(4);
//		
//			// second
//			x = this.cvs(map2[dimx][ir], sourceX, xsize) - listenerXOffset;
//			y = this.cvs(map2[dimy][ir], sourceY, ysize) - listenerYOffset;
//			z = this.cvs(map2[dimz][ir], sourceZ, zsize) - listenerZOffset;
//			spher = this.ctos(x, y, z);
//			second[aZ] = spher[0];
//			second[eL] = spher[1];
//			r = spher[2];
//			//"spher: %\n".postf(spher);
//			second[delay] = r - source[delay];
//			//"second[delay]: %\n".postf(second[delay]);
//			second[scale] = source[delay]/(source[delay] + second[delay]);
//			//"second[scale]: %\n".postf(second[scale]);
//			
//			"%	%	%	".postf(map2[dimx][ir], map2[dimy][ir], map2[dimz][ir]);
//			
//			// third +
//			x = this.cvs(map3[dimx][ir], sourceX, xsize) - listenerXOffset;
//			y = this.cvs(map3[dimy][ir], sourceY, ysize) - listenerYOffset;
//			z = this.cvs(map3[dimz][ir], sourceZ, zsize) - listenerZOffset;
//			spher = this.ctos(x, y, z);
//			third[aZ] = spher[0];
//			third[eL] = spher[1];
//			r = spher[2];
//			third[delay] = r - source[delay] - second[delay];
//			third[scale] = (source[delay] + second[delay])/(source[delay] + r);
//			iord = abs(map3[dimx][ir]) + abs(map3[dimy][ir]) + abs(map3[dimz][ir]) - 3;
//			// infinities happen in second[scale] here
//			if(iord == 0, {
//				//"fdelay[i]: %\n".postf(fdelay[i]);
//				second[delay] = second[delay] - fdelay[i];
//				//"second[delay]: %\n".postf(second[delay]);
//				second[scale] = fdelay[i]/(fdelay[i] + second[delay]);
//				i = i + 1;
//			});
//			//"second[scale]: %\n".postf(second[scale]);
//			secondRefs = secondRefs.add(second); // az, el, delay, scale
//			thirdRefs = thirdRefs.add(third); // az, el, delay, scale
//			
//			"2nd:		%	%	%	%\n".postf(second[aZ], second[eL], second[delay], second[scale]);
//			"%	%	%	".postf(map3[dimx][ir], map3[dimy][ir], map3[dimz][ir]);
//			"%				%	%\n".postf(ord[iord], third[delay], third[scale]);
//		});
//		^[firstRefs, secondRefs, thirdRefs];
//	}
	
	calcReflections { |az, el, r| 
		var source, first, second, third, fourth, fdelay;
		var x, y, z, sourceX, sourceY, sourceZ, sum, sum2, avg, sd;
		var ix, iy, iz, i, j, k, iord;
		var firstRefs, secondRefs, thirdRefs;
		var spher;
		var ord = #["3rd:", "4th:"];
		
		source = Array.newClear(4);
		
		fdelay = Array.newClear(6);
		
		firstRefs = IdentityDictionary.new;
		secondRefs = IdentityDictionary.new;
		thirdRefs = IdentityDictionary.new;
		
		source[aZ] = az;
		source[eL] = el;
		
		// convert meters to seconds
		// moved above to avoid repeatedly doing this
//		listenerXOffset = listenerXOffset * spm;
//		listenerYOffset = listenerYOffset * spm;
//		listenerZOffset = listenerZOffset * spm;
//		xsize = xsize * spm;
//		ysize = ysize * spm;
//		zsize = zsize * spm;
		
		// calc direct then shift origin
		#sourceX, sourceY, sourceZ = this.stoc(az, el, r * spm);
		source[delay] = sqrt(sourceX.squared + sourceY.squared + sourceZ.squared); // direct sound path
		
		"source: %, %, %, %\n".postf(source[aZ], source[eL], source[delay], refdist / r);
		
		// shift origin to room center
		sourceX = sourceX + listenerXOffset;
		sourceY = sourceY + listenerYOffset;
		sourceZ = sourceZ + listenerZOffset;
		
		// calc coords of image model virtual sources
		"ix	iy	iz	order	az	el	delay	scale".postln;
		
		
		// first order
		6.do({|ir|
			first = Array.newClear(4);
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
			firstRefs[this.roomSymbol(map1[dimx][ir], map1[dimy][ir], map1[dimz][ir])] = first; // az, el, delay, scale
			
			"%	%	%	".postf(map1[dimx][ir], map1[dimy][ir], map1[dimz][ir]);
			"1st:		%	%	%	%\n".postf(first[aZ], first[eL], first[delay], first[scale]);
		});
		
		// second and higher
		i = 0;
		18.do({|ir|
			second = Array.newClear(4);
			third = Array.newClear(4);
		
			// second
			x = this.cvs(map2[dimx][ir], sourceX, xsize) - listenerXOffset;
			y = this.cvs(map2[dimy][ir], sourceY, ysize) - listenerYOffset;
			z = this.cvs(map2[dimz][ir], sourceZ, zsize) - listenerZOffset;
			spher = this.ctos(x, y, z);
			second[aZ] = spher[0];
			second[eL] = spher[1];
			r = spher[2];
			//"spher: %\n".postf(spher);
			second[delay] = r - source[delay];
			//"second[delay]: %\n".postf(second[delay]);
			second[scale] = source[delay]/(source[delay] + second[delay]);
			//"second[scale]: %\n".postf(second[scale]);
			
			"%	%	%	".postf(map2[dimx][ir], map2[dimy][ir], map2[dimz][ir]);
			
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
			// infinities happen in second[scale] here
			if(iord == 0, {
				//"fdelay[i]: %\n".postf(fdelay[i]);
				second[delay] = second[delay] - fdelay[i];
				//"second[delay]: %\n".postf(second[delay]);
				second[scale] = fdelay[i]/(fdelay[i] + second[delay]);
				i = i + 1;
			});
			//"second[scale]: %\n".postf(second[scale]);
			// az, el, delay, scale
			secondRefs[this.roomSymbol(map2[dimx][ir], map2[dimy][ir], map2[dimz][ir])] = second;
			thirdRefs[this.roomSymbol(map3[dimx][ir], map3[dimy][ir], map3[dimz][ir])] = third;			
			"2nd:		%	%	%	%\n".postf(second[aZ], second[eL], second[delay], second[scale]);
			"%	%	%	".postf(map3[dimx][ir], map3[dimy][ir], map3[dimz][ir]);
			"%				%	%\n".postf(ord[iord], third[delay], third[scale]);
		});
		^[firstRefs, secondRefs, thirdRefs];
	}

	//private
	
	// cartesian to spherical
	ctos { |x, y, z|
		var az, el, r, rad, offset;
		
		r = sqrt(x.squared + y.squared + z.squared);
		el = asin(z/r) * dpr;
		//if(x == 0, {x = tiny});
		x = if(x.abs > 0, x, tiny); // no divide by 0
		
		rad = atan(y/x);
		//if(x > 0, {az = 90 - (rad * dpr)});
//		if(x < 0, {az = 270 - (rad * dpr)});
		
		offset = if(x > 0, 90, 270);
		az = offset - (rad * dpr);
		^[az, el, r];
	}
	
	// spherical to cartesian
	stoc {|az,el, r|
		var x,y,z;
		z = sin(el * rpd) * r;
		r = sqrt(r.squared - z.squared);
		x = cos((90 -az) * rpd) * r;
		y = sin((90 - az) * rpd) * r;
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
			});
			vs = ic * cr + vs;
		})
		^vs;
	}
}

BMPlaneSurface : BMAbstractSpaceModel {
	
}

// components can be used to efficiently combine
BMEarlyReflections {
	
	classvar spm = 0.0034;

	*ar {|input, sourceAzi, sourceEle, sourceDist, room, vbapBuf, numChans, coef = 0.99, fbScale = 0.9, spread = 1, refDist = 1, split = false|
		var source, delayedSource, filtered1, filtered2, sourceAtten, refDistRecip;
		var firstReflecs, secondReflecs, secondReflecsDir, secondReflecsInDir, thirdPlusReflecs;
		var firstRefDel, secondRefDel, firstBuf, secondBuf;
		var rUnits, crossFeedInputs;
		var fmCrossFeeds, fmR1inputs, fmR1delays;
		var roomMaxDelay;
		var final, sourceDelay;
		
		var absorp = 0.015, synthHiFreqRef=4000;
		var temp1, temp2, useAirAbsorp = true;
		
		refDistRecip = 1 / refDist;
//		r1DelIndices = room.r1DelIndices;
//		r2DelOneIndices = room.r2DelOneIndices;
		
		// k->[az, el, delay, scale]
		#firstReflecs, secondReflecs, thirdPlusReflecs = room.calcReflections(sourceAzi, sourceEle, sourceDist); 
		
		// sort out direct second order
		// those with "2" in them are behind first order rooms and will be represented with R2s
		//secondReflecsDir = secondReflecs.reject({|item, key| key.asString.contains("2") });
		//secondReflecsInDir = secondReflecs.select({|item, key| key.asString.contains("2") });
		
		roomMaxDelay = room.maxDelay;
		
		sourceAtten = (sourceDist * refDistRecip).reciprocal;
		
		"del: %\n".postf(sourceDist * spm);
		"sourceAtten: %\n".postf(sourceAtten);
		"roomMaxDelay: %\n".postf(roomMaxDelay);
		
		sourceDelay = sourceDist * spm;
		useAirAbsorp.if({
			temp1 = 1 - ( (1 - absorp) ** (2 * sourceDist) );    
			temp2 = 2 * ( ( (1 - temp1) * ( (pi/22050) * synthHiFreqRef).cos ) - 1 ); 
			coef =  ( temp2.neg - ( ( temp2.squared - (4 * temp1.squared) ).sqrt ) ) / ( 2 * temp1); 
			delayedSource = DelayC.ar(OnePole.ar(input, coef, sourceAtten), roomMaxDelay, sourceDelay);
		},{
			delayedSource = DelayC.ar(input * sourceAtten, roomMaxDelay, sourceDelay);
		});
		
		//delayedSource = input * sourceAtten;
		
		// should add distance filtering here
		source = VBAP.ar(numChans, delayedSource, vbapBuf, sourceAzi, sourceEle, spread);
		//source = VBAP.ar(numChans, input * sourceAtten, vbapBuf, sourceAzi, sourceEle, spread);
		
		// filter source to model absorption for first and second order reflections
		useAirAbsorp.if({
			var avgFirstDist, avgSecondDist;
			avgFirstDist = firstReflecs.collectAs({|v, k| v[2]},Array).mean;
			temp1 = 1 - ( (1 - absorp) ** (2 * avgFirstDist) );    
			temp2 = 2 * ( ( (1 - temp1) * ( (pi/SampleRate.ir * 0.5) * synthHiFreqRef).cos ) - 1 ); 
			coef =  ( temp2.neg - ( ( temp2.squared - (4 * temp1.squared) ).sqrt ) ) / ( 2 * temp1); 
			filtered1 = OnePole.ar(delayedSource, coef);
			
			avgSecondDist = firstReflecs.collectAs({|v, k| v[2]},Array).mean;
			temp1 = 1 - ( (1 - absorp) ** (2 * avgFirstDist) );    
			temp2 = 2 * ( ( (1 - temp1) * ( (pi/SampleRate.ir * 0.5) * synthHiFreqRef).cos ) - 1 ); 
			coef =  ( temp2.neg - ( ( temp2.squared - (4 * temp1.squared) ).sqrt ) ) / ( 2 * temp1); 	
			filtered2 = OnePole.ar(filtered1, coef);
		
		}, {
			filtered1 = OnePole.ar(delayedSource, coef);
			filtered2 = OnePole.ar(filtered1, coef);
		});
		
//		//write the above to delay lines
//		filtered1 = DelTapWr.ar(firstBuf = LocalBuf(SampleRate.ir * roomMaxDelay).clear, filtered1);
//		filtered2 = DelTapWr.ar(secondBuf = LocalBuf(SampleRate.ir * roomMaxDelay).clear, filtered2);
//		
//		// delay the first order reflections
//		firstRefDel = firstReflecs.collect({|ref, k| 
//			//Poll(ref[2] <= ControlDur.ir, ref[2], k);
//			DelTapRd.ar(firstBuf, filtered1, max(ref[2], ControlDur.ir), 4, ref[3]);
//		});
//		
//		// delay the direct second order reflections
//
//		secondRefDel = secondReflecsDir.collect({|ref, k| 
//			//Poll(ref[2] <= ControlDur.ir, ref[2], k);
//			DelTapRd.ar(secondBuf, filtered2, max(ref[2], ControlDur.ir), 4, ref[3]);
//		});

		// delay the first order reflections
		firstRefDel = firstReflecs.collect({|ref, k| 
			//Poll(ref[2] <= ControlDur.ir, ref[2], k);
			DelayC.ar(filtered1, roomMaxDelay, ref[2], ref[3]);
		});
		
		// delay the direct second order reflections

		secondRefDel = secondReflecs.collect({|ref, k| 
			DelayC.ar(filtered2, roomMaxDelay, ref[2], ref[3]);
		});

				
		/// now pan everything...
		
		// pan direct second order + R1s
		secondRefDel = secondRefDel.collect({|del, k|
			var ref, input;
			ref = secondReflecs[k];
			input = del;
			VBAP.ar(numChans, input, vbapBuf, ref[0], ref[1], spread);
		});
		
		// pan first order + R2s
		firstRefDel = firstRefDel.collect({|del, k|
			var ref, input;
			ref = firstReflecs[k];
			input = del;
			VBAP.ar(numChans, input, vbapBuf, ref[0], ref[1], spread);
		});
		
		final = [source, Mix(firstRefDel.values), Mix(secondRefDel.values)];
		^if(split, {final}, {Mix(final)});
		
		//^source
	}
	
}

BMDiffuseReverb { }

// this manages multiple sources as a whole
BMSourceModeler { }

// a la Kendall and Mertens
BMSpatialReverberator {
	
	classvar spm = 0.0034;
	
	// source coords relative to listener pos?
//	*arOld {|input, sourceAzi, sourceEle, sourceDist, room, vbapBuf, numChans, coef = 0.99, fbScale = 0.9, spread = 1, refDist = 1|
//		var source, delayedSource, filtered1, filtered2, sourceAtten, refDistRecip;
//		var firstReflecs, secondReflecs, secondReflecsDir , thirdPlusReflecs;
//		var firstRefDel, secondRefDel;
//		var r1delays, r2delays, r1DelIndices, r2DelOneIndices, crossFeedIndices, r2inputs;
//		var roomMaxDelay;
//		
//		refDistRecip = 1 / refDist;
//		r1DelIndices = room.r1DelIndices;
//		r2DelOneIndices = room.r2DelOneIndices;
//		crossFeedIndices = room.crossFeedIndices;
//		
//		// [az, el, delay, scale]
//		#firstReflecs, secondReflecs, thirdPlusReflecs = room.calcReflections(sourceAzi, sourceEle, sourceDist); 
//		
//		// sort out direct second order
//		secondReflecsDir = secondReflecs.reject({|item, i| r2DelOneIndices.indexOf(i).notNil });
//		
//		firstReflecs = firstReflecs.flop;
//		secondReflecs = secondReflecs.flop;
//		thirdPlusReflecs = thirdPlusReflecs.flop;
//		secondReflecsDir = secondReflecsDir.flop;
//		
//		roomMaxDelay = room.maxDelay;
//		
//		sourceAtten = (sourceDist * refDistRecip).reciprocal;
//		
//		"del: %\n".postf(sourceDist * spm);
//		"sourceAtten: %\n".postf(sourceAtten);
//		"roomMaxDelay: %\n".postf(roomMaxDelay);
//		
//		// source + az seems to crackle, could do without
//		delayedSource = BufRdDelay.ar(input * sourceAtten, roomMaxDelay, sourceDist * spm).postln;
//
//		// filter source to model absorption
//		filtered1 = OnePole.ar(input, coef);
//		filtered2 = OnePole.ar(filtered1, coef);
//		
//		// should add distance filtering here
//		source = VBAP.ar(numChans, delayedSource, vbapBuf, sourceAzi, sourceEle, spread);
//		//source = VBAP.ar(numChans, input * sourceAtten, vbapBuf, sourceAzi, sourceEle, spread);
//		
//		
//		////// second order and R1 //////
//		
//		// need to delay delay times...
//		secondRefDel = MultiBufRdDelay.ar(filtered2, roomMaxDelay * 2, secondReflecsDir[2]);
//		
//		// could refine max delay time here
//		r1delays = R1.ar(secondRefDel, roomMaxDelay * 2, thirdPlusReflecs[2][r1DelIndices] - secondReflecs[2][r1DelIndices], coef, fbScale);
//		
//		"r1Delays: %\n".postf(r1delays);
//		
//		// pan second order this should be only the direct ones.
//		secondRefDel = VBAP.ar(numChans, secondRefDel + r1delays, vbapBuf, secondReflecsDir[0], secondReflecsDir[1], spread) * secondReflecsDir[3];
//
//		
//		////// first order and R2 //////
//		
//		// need to delay delay times...
//		firstRefDel = MultiBufRdDelay.ar(filtered1, roomMaxDelay * 2, firstReflecs[2]);
//		\foo.postln;
//		// sum in the adjacent R1 streams
//		r2inputs = firstRefDel.collect({|delayed, i| delayed + Mix(r1delays[crossFeedIndices[i].postln]) });
//		\bar.postln;
//		// could refine max delay time here
//		r2delays = R2.ar(r2inputs, roomMaxDelay * 2, secondReflecs[2][r2DelOneIndices] - firstReflecs[2], roomMaxDelay * 2, thirdPlusReflecs[2][r2DelOneIndices] - secondReflecs[2][r2DelOneIndices], coef, fbScale);
//		
//		// pan first order
//		firstRefDel = VBAP.ar(numChans, firstRefDel + r2delays, vbapBuf, firstReflecs[0], firstReflecs[1], spread) * firstReflecs[3];
//		
//		^firstRefDel + secondRefDel + source;
//		//^source
//	}

//	*ar {|input, sourceAzi, sourceEle, sourceDist, room, vbapBuf, numChans, coef = 0.99, fbScale = 0.9, spread = 1, refDist = 1|
//		var source, delayedSource, filtered1, filtered2, sourceAtten, refDistRecip;
//		var firstReflecs, secondReflecs, secondReflecsDir, secondReflecsInDir, thirdPlusReflecs;
//		var firstRefDel, secondRefDel, firstBuf, secondBuf;
//		var rUnits, crossFeedInputs;
//		var fmCrossFeeds, fmR1inputs, fmR1delays;
//		var roomMaxDelay;
//		
//		var absorp = 0.015;
//		var temp1, temp2, useAirAbsorp = true;
//		
//		refDistRecip = 1 / refDist;
////		r1DelIndices = room.r1DelIndices;
////		r2DelOneIndices = room.r2DelOneIndices;
//		
//		// k->[az, el, delay, scale]
//		#firstReflecs, secondReflecs, thirdPlusReflecs = room.calcReflections(sourceAzi, sourceEle, sourceDist); 
//		
//		// sort out direct second order
//		// those with "2" in them are behind first order rooms and will be represented with R2s
//		secondReflecsDir = secondReflecs.reject({|item, key| key.asString.contains("2") });
//		secondReflecsInDir = secondReflecs.select({|item, key| key.asString.contains("2") });
//		
//		roomMaxDelay = room.maxDelay;
//		
//		sourceAtten = (sourceDist * refDistRecip).reciprocal;
//		
//		"del: %\n".postf(sourceDist * spm);
//		"sourceAtten: %\n".postf(sourceAtten);
//		"roomMaxDelay: %\n".postf(roomMaxDelay);
//		
//		delayedSource = DelayC.ar(input * sourceAtten, roomMaxDelay, sourceDist * spm);
//		
//		//delayedSource = input * sourceAtten;
//		
//		// should add distance filtering here
//		source = VBAP.ar(numChans, delayedSource, vbapBuf, sourceAzi, sourceEle, spread);
//		//source = VBAP.ar(numChans, input * sourceAtten, vbapBuf, sourceAzi, sourceEle, spread);
//		
//		// filter source to model absorption for first and second order reflections
//		filtered1 = OnePole.ar(delayedSource, coef);
//		filtered2 = OnePole.ar(filtered1, coef);
//		
////		//write the above to delay lines
////		filtered1 = DelTapWr.ar(firstBuf = LocalBuf(SampleRate.ir * roomMaxDelay).clear, filtered1);
////		filtered2 = DelTapWr.ar(secondBuf = LocalBuf(SampleRate.ir * roomMaxDelay).clear, filtered2);
////		
////		// delay the first order reflections
////		firstRefDel = firstReflecs.collect({|ref, k| 
////			//Poll(ref[2] <= ControlDur.ir, ref[2], k);
////			DelTapRd.ar(firstBuf, filtered1, max(ref[2], ControlDur.ir), 4, ref[3]);
////		});
////		
////		// delay the direct second order reflections
////
////		secondRefDel = secondReflecsDir.collect({|ref, k| 
////			//Poll(ref[2] <= ControlDur.ir, ref[2], k);
////			DelTapRd.ar(secondBuf, filtered2, max(ref[2], ControlDur.ir), 4, ref[3]);
////		});
//
//		// delay the first order reflections
//		firstRefDel = firstReflecs.collect({|ref, k| 
//			//Poll(ref[2] <= ControlDur.ir, ref[2], k);
//			DelayC.ar(filtered1, roomMaxDelay, ref[2], ref[3]);
//		});
//		
//		//"frd: %\n".postf(firstRefDel);
//		
//		// delay the direct second order reflections
//
//		secondRefDel = secondReflecsDir.collect({|ref, k| 
//			DelayC.ar(filtered2, roomMaxDelay, ref[2], ref[3]);
//		});
//
//		//"srd: %\n".postf(secondRefDel);
//		
//		//// Now RUnits ////
//		//// Need to create in this order to allow for crossfeeds
//		
//		rUnits = IdentityDictionary.new;
//		
//		crossFeedInputs = (
//			'001'Ê: ['011',Ê'0-11',Ê'101',Ê'-101'],
//			'00-1' : ['01-1',Ê'0-1-1',Ê'10-1', '-10-1'],
//			'100' : ['101', '10-1'],
//			'-100' : ['-101', '-10-1'],
//			'010'Ê: ['011',Ê'01-1'],
//			'0-10'Ê: ['0-1-1',Ê'0-11'],
//			'110': ['010', '100'],
//			'-110': ['010', '-100'],
//			'-1-10' : ['-100', '0-10'],
//			'1-10' : ['0-10', '100']
//		);
//
//		// R1s that aren't crossfed
//		['101', '10-1', '-101', '-10-1', '011', '01-1', '0-1-1', '0-11'].do({|k|
//			var input, sec, third, thirdKey;
//			input = secondRefDel[k];
//			sec = secondReflecs[k][2];
//			thirdKey = k.asString.tr($1, $2).asSymbol; // move out one room in the same direction
//			third = thirdPlusReflecs[thirdKey][2];
//			rUnits[k] = R1C.ar(input, roomMaxDelay * 2, third - sec, coef, fbScale);
//		});
//		
//
//		// All R2s, which are fed from the previous R1s
//
//		['001', '00-1', '100', '-100', '010', '0-10'].do({|k|
//			var input, first, secKey, thirdKey, sec, third;
//			input = [firstRefDel[k]] ++ crossFeedInputs[k].collect({|key| rUnits[key]});
//			input = Mix(input);
//			//"R2 In %\n".postf(input);
//			first = firstReflecs[k][2];
//			secKey = k.asString.tr($1, $2).asSymbol; // move out one room in the same direction
//			thirdKey = k.asString.tr($1, $3).asSymbol; // move out two rooms in the same direction
//			sec = secondReflecs[secKey][2];
//			third = thirdPlusReflecs[thirdKey][2];
//			rUnits[k] = R2C.ar(input, roomMaxDelay * 2, sec - first, roomMaxDelay * 2, third - sec, coef, fbScale);
//		});
//
//		// R1s that are fed from the above
//		['110', '-110', '-1-10', '1-10'].do({|k|
//			var input, sec, third, thirdKey;
//			input = [secondRefDel[k]] ++ crossFeedInputs[k].collect({|key| rUnits[key]});
//			input = Mix(input);
//			//"R1 In %\n".postf(input);
//			sec = secondReflecs[k][2];
//			thirdKey = k.asString.tr($1, $2).asSymbol; // move out one room in the same direction
//			third = thirdPlusReflecs[thirdKey][2];
//			rUnits[k] = R1C.ar(input, roomMaxDelay * 2, third - sec, coef, fbScale);
//		});
//		
//		/// now mix and pan everything...
//		
//		// pan direct second order + R1s
//		secondRefDel = secondRefDel.collect({|del, k|
//			var ref, input;
//			ref = secondReflecsDir[k];
//			input = Mix([del, rUnits[k]]);
//			//input = del;
//			//"sec In %\n".postf(input);
//			VBAP.ar(numChans, input, vbapBuf, ref[0], ref[1], spread);
//		});
//		
//		// pan first order + R2s
//		firstRefDel = firstRefDel.collect({|del, k|
//			var ref, input;
//			ref = firstReflecs[k];
//			input = Mix([del, rUnits[k]]);
//			//"first In %\n".postf(input);
//			//input = del;
//			VBAP.ar(numChans, input, vbapBuf, ref[0], ref[1], spread);
//		});
//		
//		^Mix(firstRefDel.values) + Mix(secondRefDel.values) + source;
//		//^source
//	}

	*ar {|input, sourceAzi, sourceEle, sourceDist, room, vbapBuf, numChans, coef = 0.99, fbScale = 0.9, spread = 1, refDist = 1|
		var source, delayedSource, filtered1, filtered2, sourceAtten, refDistRecip;
		var firstReflecs, secondReflecs, secondReflecsDir, secondReflecsInDir, thirdPlusReflecs;
		var firstRefDel, secondRefDel, firstBuf, secondBuf;
		var rUnits, crossFeedInputs;
		var fmCrossFeeds, fmR1inputs, fmR1delays;
		var roomMaxDelay, sourceDelay;
		
		var absorp = 0.015, synthHiFreqRef=4000;
		var temp1, temp2, useAirAbsorp = true;
		
		refDistRecip = 1 / refDist;
//		r1DelIndices = room.r1DelIndices;
//		r2DelOneIndices = room.r2DelOneIndices;
		
		// k->[az, el, delay, scale]
		#firstReflecs, secondReflecs, thirdPlusReflecs = room.calcReflections(sourceAzi, sourceEle, sourceDist); 
		
		// sort out direct second order
		// those with "2" in them are behind first order rooms and will be represented with R2s
		secondReflecsDir = secondReflecs.reject({|item, key| key.asString.contains("2") });
		secondReflecsInDir = secondReflecs.select({|item, key| key.asString.contains("2") });
		
		roomMaxDelay = room.maxDelay;
		
		sourceAtten = (sourceDist * refDistRecip).reciprocal;
		
		"del: %\n".postf(sourceDist * spm);
		"sourceAtten: %\n".postf(sourceAtten);
		"roomMaxDelay: %\n".postf(roomMaxDelay);
		
		sourceDelay = sourceDist * spm;
		useAirAbsorp.if({
			temp1 = 1 - ( (1 - absorp) ** (2 * sourceDist) );    
			temp2 = 2 * ( ( (1 - temp1) * ( (pi/22050) * synthHiFreqRef).cos ) - 1 ); 
			coef =  ( temp2.neg - ( ( temp2.squared - (4 * temp1.squared) ).sqrt ) ) / ( 2 * temp1); 
			delayedSource = DelayC.ar(OnePole.ar(input, coef, sourceAtten), roomMaxDelay, sourceDelay);
		},{
			delayedSource = DelayC.ar(input * sourceAtten, roomMaxDelay, sourceDelay);
		});
		
		//delayedSource = input * sourceAtten;
		
		// should add distance filtering here
		source = VBAP.ar(numChans, delayedSource, vbapBuf, sourceAzi, sourceEle, spread);
		//source = VBAP.ar(numChans, input * sourceAtten, vbapBuf, sourceAzi, sourceEle, spread);
		
		// filter source to model absorption for first and second order reflections
		useAirAbsorp.if({
			var avgFirstDist, avgSecondDist;
			avgFirstDist = firstReflecs.collectAs({|v, k| v[2]},Array).mean;
			temp1 = 1 - ( (1 - absorp) ** (2 * avgFirstDist) );    
			temp2 = 2 * ( ( (1 - temp1) * ( (pi/SampleRate.ir * 0.5) * synthHiFreqRef).cos ) - 1 ); 
			coef =  ( temp2.neg - ( ( temp2.squared - (4 * temp1.squared) ).sqrt ) ) / ( 2 * temp1); 
			filtered1 = OnePole.ar(delayedSource, coef);
			
			avgSecondDist = firstReflecs.collectAs({|v, k| v[2]},Array).mean;
			temp1 = 1 - ( (1 - absorp) ** (2 * avgFirstDist) );    
			temp2 = 2 * ( ( (1 - temp1) * ( (pi/SampleRate.ir * 0.5) * synthHiFreqRef).cos ) - 1 ); 
			coef =  ( temp2.neg - ( ( temp2.squared - (4 * temp1.squared) ).sqrt ) ) / ( 2 * temp1); 	
			filtered2 = OnePole.ar(filtered1, coef);
		
		}, {
			filtered1 = OnePole.ar(delayedSource, coef);
			filtered2 = OnePole.ar(filtered1, coef);
		});

		// delay the first order reflections
		firstRefDel = firstReflecs.collect({|ref, k| 
			//Poll(ref[2] <= ControlDur.ir, ref[2], k);
			DelayC.ar(filtered1, roomMaxDelay, ref[2], ref[3]);
		});
		
		//"frd: %\n".postf(firstRefDel);
		
		// delay the direct second order reflections

		secondRefDel = secondReflecsDir.collect({|ref, k| 
			DelayC.ar(filtered2, roomMaxDelay, ref[2], ref[3]);
		});

		//"srd: %\n".postf(secondRefDel);
		
		//// Now RUnits ////
		//// Need to create in this order to allow for crossfeeds
		
		rUnits = IdentityDictionary.new;
		
		crossFeedInputs = (
			'001'Ê: ['011',Ê'0-11',Ê'101',Ê'-101'],
			'00-1' : ['01-1',Ê'0-1-1',Ê'10-1', '-10-1'],
			'100' : ['101', '10-1'],
			'-100' : ['-101', '-10-1'],
			'010'Ê: ['011',Ê'01-1'],
			'0-10'Ê: ['0-1-1',Ê'0-11'],
			'110': ['010', '100'],
			'-110': ['010', '-100'],
			'-1-10' : ['-100', '0-10'],
			'1-10' : ['0-10', '100']
		);

		// R1s that aren't crossfed
		['101', '10-1', '-101', '-10-1', '011', '01-1', '0-1-1', '0-11'].do({|k|
			var input, sec, third, thirdKey;
			input = secondRefDel[k];
			sec = secondReflecs[k][2];
			thirdKey = k.asString.tr($1, $2).asSymbol; // move out one room in the same direction
			third = thirdPlusReflecs[thirdKey][2];
			rUnits[k] = R1C.ar(input, roomMaxDelay * 2, third - sec, coef, fbScale);
		});
		

		// All R2s, which are fed from the previous R1s

		['001', '00-1', '100', '-100', '010', '0-10'].do({|k|
			var input, first, secKey, thirdKey, sec, third;
			input = [firstRefDel[k]] ++ crossFeedInputs[k].collect({|key| rUnits[key]});
			input = Mix(input);
			//"R2 In %\n".postf(input);
			first = firstReflecs[k][2];
			secKey = k.asString.tr($1, $2).asSymbol; // move out one room in the same direction
			thirdKey = k.asString.tr($1, $3).asSymbol; // move out two rooms in the same direction
			sec = secondReflecs[secKey][2];
			third = thirdPlusReflecs[thirdKey][2];
			rUnits[k] = R2C.ar(input, roomMaxDelay * 2, sec - first, roomMaxDelay * 2, third - sec, coef, fbScale);
		});

		// R1s that are fed from the above
		['110', '-110', '-1-10', '1-10'].do({|k|
			var input, sec, third, thirdKey;
			input = [secondRefDel[k]] ++ crossFeedInputs[k].collect({|key| rUnits[key]});
			input = Mix(input);
			//"R1 In %\n".postf(input);
			sec = secondReflecs[k][2];
			thirdKey = k.asString.tr($1, $2).asSymbol; // move out one room in the same direction
			third = thirdPlusReflecs[thirdKey][2];
			rUnits[k] = R1C.ar(input, roomMaxDelay * 2, third - sec, coef, fbScale);
		});
		
		/// now mix and pan everything...
		
		// pan direct second order + R1s
		secondRefDel = secondRefDel.collect({|del, k|
			var ref, input;
			ref = secondReflecsDir[k];
			input = Mix([del, rUnits[k]]);
			//input = del;
			//"sec In %\n".postf(input);
			VBAP.ar(numChans, input, vbapBuf, ref[0], ref[1], spread);
		});
		
		// pan first order + R2s
		firstRefDel = firstRefDel.collect({|del, k|
			var ref, input;
			ref = firstReflecs[k];
			input = Mix([del, rUnits[k]]);
			//"first In %\n".postf(input);
			//input = del;
			VBAP.ar(numChans, input, vbapBuf, ref[0], ref[1], spread);
		});
		
		^Mix(firstRefDel.values) + Mix(secondRefDel.values) + source;
		//^source
	}
	
}


// correctly multichannel expand the pseudo UGens below
// rate in new1 methods a hook for future kr versions
PseudoMultiNewUGen {
	
	*multiNewList { arg args;
		var size = 0, newArgs, results;
		args = args.asUGenInput(this);
		args.do({ arg item;
			(item.class == Array).if({ size = max(size, item.size) });
		});
		if (size == 0) { ^this.new1( *args ) };
		newArgs = Array.newClear(args.size);
		results = Array.newClear(size);
		size.do({ arg i;
			args.do({ arg item, j;
				newArgs.put(j, if (item.class == Array, { item.wrapAt(i) },{ item }));
			});
			results.put(i, this.multiNewList(newArgs));
		});
		^results
	}
}

// pseudo Ugen for audio rate interp
BufRdDelay : PseudoMultiNewUGen {
	
	*ar {|in, maxDelayTime, delayTime|
		^this.multiNewList(['audio', in, maxDelayTime, delayTime]);
	}
	
	*new1 {|rate, in, maxDelayTime, delayTime|
		var buf, phasor, maxFrames, sr, out;
		sr = SampleRate.ir;
		maxFrames = maxDelayTime * sr;
		buf = LocalBuf(maxFrames, 1).clear;
		phasor = Phasor.ar(0, 1, 0, maxFrames);
		//out = BufRd.ar(1, buf, phasor + (delayTime * sr) - (ControlDur.ir * sr) % maxFrames, 1, 2);
		//out = BufRd.ar(1, buf, phasor + (delayTime * sr) % maxFrames, 1, 2);
		//out = BufRd.ar(1, buf, phasor + (delayTime * sr), 1, 2);
//		BufWr.ar(in, buf, phasor, 1);
		out = BufRd.ar(1, buf, phasor, 1, 4);
		BufWr.ar(in, buf, phasor + (delayTime * sr), 1);
		^out
	}
}

MultiBufRdDelay : PseudoMultiNewUGen {
	
	*ar {|in, maxDelayTime, delayTimes|
		^this.multiNewList(['audio', in, maxDelayTime, `delayTimes]);
	}
	
	*new1 {|rate, in, maxDelayTime, delayTimes|
		var buf, phasor, maxFrames, sr, cd, out;
		sr = SampleRate.ir;
		maxFrames = maxDelayTime * sr;
		buf = LocalBuf(maxFrames, 1).clear;
		phasor = Phasor.ar(0, 1, 0, maxFrames);
		//cd = ControlDur.ir;
		out = delayTimes.dereference.collect({|delayTime|
			//BufRd.ar(1, buf, phasor + (delayTime * sr) - (cd * sr) % maxFrames, 1, 2);
			BufRd.ar(1, buf, phasor - (delayTime * sr), 1, 4);
		});
		BufWr.ar(in, buf, phasor, 1);
		^out
	}
}
	

// Kendall-Mertens comb units
R1 : PseudoMultiNewUGen {
	
	*ar {|in, maxDelayTime, delayTime, coef = 0.99, fbScale = 0.9|
		^this.multiNewList(['audio', in, maxDelayTime, delayTime, coef, fbScale]);
	}
	
	*new1 {|rate, in, maxDelayTime, delayTime, coef, fbScale|
		var buf, phasor, maxFrames, sr, ff;
		//Poll(delayTime <= ControlDur.ir, delayTime, 'R1');
		//delayTime = (max(delayTime, ControlDur.ir) + LFNoise2.kr(100).range(0, 0.0008)).poll(label: UniqueID.next.asString);
		//delayTime = (max(delayTime, ControlDur.ir));
		sr = SampleRate.ir;
		maxFrames = maxDelayTime * sr;
		buf = LocalBuf(maxFrames, 1).clear;
		phasor = Phasor.ar(0, 1, 0, maxFrames);
		//ff = BufRd.ar(1, buf, phasor + (delayTime * sr) - (ControlDur.ir * sr) % maxFrames, 1, 2);
		ff = BufRd.ar(1, buf, phasor, 1, 4);
		ff = OnePole.ar(ff, coef) * fbScale;
		BufWr.ar(in + ff, buf, phasor + (delayTime * sr), 1);
		^ff
	}
}

R1C : UGen {

	*ar { arg in = 0.0, maxdelaytime = 0.2, delaytime = 0.2, coef = 0.99, fbScale = 0.9;
		^this.multiNew('audio', in.asAudioRateInput, maxdelaytime, delaytime, coef, fbScale);
	}
}

R2C : UGen {

	*ar { arg in = 0.0, maxdelaytime1 = 0.2, delaytime1 = 0.2, maxdelaytime2 = 0.2, delaytime2 = 0.2, coef = 0.99, fbScale = 0.9;
		^this.multiNew('audio', in.asAudioRateInput, maxdelaytime1, delaytime1, maxdelaytime2, delaytime2, coef, fbScale);
	}
}

R2 : PseudoMultiNewUGen {
	
	*ar {|in, maxDelayTime1, delayTime1, maxDelayTime2, delayTime2, coef = 0.99, fbScale = 0.9|
		^this.multiNewList(['audio', in, maxDelayTime1, delayTime1, maxDelayTime2, delayTime2, coef, fbScale]);
	}
	
	*new1 {|rate, in, maxDelayTime1, delayTime1, maxDelayTime2, delayTime2, coef, fbScale|
		var buf1, phasor1, maxFrames1, sr, ff1, out;
		var buf2, phasor2, maxFrames2, ff2;
		sr = SampleRate.ir;
		Poll.ar(delayTime1 <= ControlDur.ir, delayTime1, 'R2-1');
		Poll.ar(delayTime2 <= ControlDur.ir, delayTime2, 'R2-2');
		//delayTime1 = max(delayTime1, ControlDur.ir)  + LFNoise2.kr(100).range(0, 0.0008);
//		delayTime2 = max(delayTime2, ControlDur.ir)  + LFNoise2.kr(100).range(0, 0.0008);
		//delayTime1 = max(delayTime1, ControlDur.ir);
		//delayTime2 = max(delayTime2, ControlDur.ir);
		
		// delay1 params
		maxFrames1 = maxDelayTime1 * sr;
		buf1 = LocalBuf(maxFrames1, 1).clear; 
		phasor1 = Phasor.ar(0, 1, 0, maxFrames1);
		
		// delay2 params
		maxFrames2 = maxDelayTime2 * sr;
		buf2 = LocalBuf(maxFrames2, 1).clear;
		phasor2 = Phasor.ar(0, 1, 0, maxFrames2);
		
		// get and filter output of delay1
		//ff1 = BufRd.ar(1, buf1, phasor1 + (delayTime1 * sr) - (ControlDur.ir * sr) % maxFrames1, 1, 2);
		ff1 = BufRd.ar(1, buf1, phasor1, 1, 4);
		ff1 = OnePole.ar(ff1, coef) * fbScale;
		
		// write ff1 to delay2
		BufWr.ar(ff1, buf2, phasor2 + (delayTime2 * sr), 1);
		
		// get and filter output of delay2
		//ff2 = BufRd.ar(1, buf2, phasor2 + (delayTime2 * sr) - (ControlDur.ir * sr) % maxFrames2, 1, 2);
		ff2 = BufRd.ar(1, buf2, phasor2, 1, 4);
		ff2 = OnePole.ar(ff2, coef) * fbScale;
		
		out = ff1 + ff2;
		// feedback into delay1
		BufWr.ar(in + ff2, buf1, phasor1 + (delayTime1 * sr), 1);
		
		^out
	}
}