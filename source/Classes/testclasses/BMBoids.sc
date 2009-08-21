BMBoid {
	var <dim, <pos, <vel;

	*new {|dim = 3, pos, vel, minDist|
		^super.newCopyArgs(dim, pos ?? {{0.5.rand2} ! dim}, vel ?? {0.0 ! dim});
	}
	
	move {|boids, centre, boundaries, velMax, velScale, minDist, avoidD, countRecip|
		vel = vel * 0.9 + 							// damp so less twitchy
			this.cohere(boids, countRecip) +			// stick together
			this.avoid(boids, minDist) +			// avoid other boids
			this.matchVel(boids, countRecip) +		// match velocity with nearby boids
			this.boundSpace(pos, boundaries, velMax);		// stay in the room, more or less
		
		avoidD.if({this.avoidDesk});				// avoid the desk
		centre.notNil.if({vel = vel + (centre - pos * 0.01)}); // move towards centering point
		vel = vel.clip2(velMax); // limit maximum velocity

		pos = (pos + (vel * velScale));
	}
	
	cohere {|boids, countRecip|
		var vec;
		vec = 0.0 ! dim;
		boids.reject({|boid| boid === this}).do({|boid| vec = vec + boid.pos });
		vec = vec * countRecip;
		^(vec - pos * 0.02)
	
	}
	
	avoid {|boids, minDist|
		var vec, posDif;
		vec = 0.0 ! dim;
		boids.reject({|boid| boid === this}).do({|boid| 
			posDif = boid.pos - pos;
			//// this is way cheaper than checking Euclidean distance each time
//			if((posDif.abs < minDist).any({|bool| bool}), {vec = vec - posDif});

			if((pos - boid.pos).squared.sum.sqrt < minDist, {vec = vec - posDif});
		});
		//postln("avoid:" + vec); 
		^vec
	}
	
	matchVel {|boids, countRecip|
		var vec;
		vec = 0 ! dim;
		boids.do({|boid| 
			if(boid !== this, { vec = vec + boid.vel });
		});
		vec = vec * countRecip;
		^(vec - vel * 0.125)
	}
	
	boundSpace{ |pos, boundaries, velMax|
		var vec;
		vec = 0 ! dim;
		pos.do({|dimension, i| 
			if(dimension < boundaries[0][i],  {vec[i] = velMax * 0.25});
			if(dimension > boundaries[1][i],  {vec[i] = velMax * -0.25});
		})
		^vec
	}
	
	avoidDesk{
		if(hypotApx(pos[0], pos[1] ) < 0.4, { vel[0] = pos[0] * 1.01 + vel[0];  vel[1] = pos[1] * 1.01 + vel[1];});
	}
	
}

// lazily updates positions when queried
// coords
BMBoidSpace {
	var <dim, <numBoids, <centre, boundaries, <velMax, <velScale, <minDist, <avoidD = false;
	var <interval = 0.05, <lastMoved;
	var <boids, boidStream, countRecip;
	
	*new {|dim = 3, numBoids, centre, boundaries, velMax, velScale, minDist, avoidD = false| 
		// boundaries is an array of [[min * dim], [max * dim]] or a speaker list
		^super.newCopyArgs(dim, numBoids, centre, boundaries, velMax, velScale ? 1.0, 
			minDist, avoidD).init;
	}
	
	init {
		var maxXinv;
		boids = { BMBoid(dim, minDist: minDist) } ! numBoids;
		boidStream = Pseq(boids, inf).asStream;
		countRecip = (numBoids - 1).reciprocal;
		lastMoved = Main.elapsedTime;
		if(boundaries.isBMInOutArray, { boundaries = boundaries.boundaries; });
		
		// normalise to maxX with 0 still centered
		maxXinv = [boundaries[0][0], boundaries[1][0]].abs.maxItem.reciprocal;
		boundaries = boundaries * maxXinv;
			
	}
	
	// setters must update position
	centre_ {|val| this.moveBoids; centre = val; }
	
	velMax_ {|val| this.moveBoids; velMax = val; }
	
	velScale_ {|val| this.moveBoids; velScale = val; }
	
	minDist_ {|val| this.moveBoids; minDist = val; }
	
	avoidD_ {|val| this.moveBoids; avoidD = val; }
	
	interval_ {|val| this.moveBoids; interval = val; }
	
	// update positions if necessary and return a boid
	next {
		this.moveBoids;
		^boidStream.next;	
	}
	
	// update positions if necessary and return an Array of Arrays with current positions
	// (2 or 3 dimensions)
	positions {
		this.moveBoids;
		^boids.collect(_.pos);
	}
	
	// lazy update based on number of intervals passed
	moveBoids {
		var timeSinceLastMoved, numMoves;
		timeSinceLastMoved = Main.elapsedTime - lastMoved;
		numMoves = (timeSinceLastMoved / interval).asInteger; // round down
		numMoves.do({
			boids.scramble.do({|boid|
				boid.move(boids, centre, boundaries, velMax, velScale * interval, // time adjust
					minDist, avoidD, countRecip);
			});
		});
		lastMoved = lastMoved + (interval * numMoves);
	}
	
}