BMGrainBoid {
	var <dim, <pos, <vel, <minDist;

	*new {|dim = 3, pos, vel, minDist|
		^super.newCopyArgs(dim, pos ?? {{0.5.rand2} ! dim}, vel ?? {0.0 ! dim}, minDist);
	}
	
	move {|boids, centre, limits, velMax, matchCountRecip|
		vel = vel + 
			(centre *  matchCountRecip ) - (pos * 0.01) + 				// move towards centering point
			this.avoid(boids) +					// avoid other boids
			this.matchVel(boids, matchCountRecip);	// match velocity with nearby boids
		vel = vel.clip2(velMax);
		pos = (pos + vel).max(limits[0]).min(limits[1]);
	}
	
	avoid {|boids|
		var vec, posDif;
		vec = 0.0 ! dim;
		boids.do({|boid| 
			posDif = pos - boid.pos;
			//// this is way cheaper than checking Euclidean distance each time
//			if((posDif.abs < minDist).any({|bool| bool}), {vec = vec - posDif});

			if((posDif.squared.sum.sqrt.abs < minDist), {vec = vec - posDif});
		});
		^vec
	}
	
	matchVel {|boids, matchCountRecip|
		var vec;
		vec = 0 ! dim;
		boids.do({|boid| 
			if(boid !== this, { vec = vec + boid.vel });
		});
		vec = vec * matchCountRecip;
		^(vec - vel * 0.125)
	}
	
}

BMGrainBoidSpace {
	var <dim, <numBoids, <>centre, limits, <velMax, <minDist;
	var <boids, boidStream, matchCountRecip;
	
	*new {|dim = 3, numBoids, centre, limits, velMax, minDist| 
		// limits is an array of [[min * dim, [max * dim]]
		^super.newCopyArgs(dim, numBoids, centre ?? {0.0 ! dim}, limits, velMax, minDist).init;
	}
	
	init {
		boids = { BMGrainBoid(dim, minDist: minDist) } ! numBoids;
		boidStream = Pseq(boids, inf).asStream;
		matchCountRecip = (numBoids - 1).reciprocal;
	}
	
	// move a boid
	moveNext {
		boidStream.next.move(boids, centre, limits, velMax, matchCountRecip);
	}
	
}