BMGrainBoid {
	var <dim, <pos, <vel;

	*new {|dim = 3, pos, vel, minDist|
		^super.newCopyArgs(dim, pos ?? {{0.5.rand2} ! dim}, vel ?? {0.0 ! dim});
	}
	
	move {|boids, centre, limits, velMax, minDist, matchCountRecip|
		vel = vel * 0.9 + 							// less twitchy
			this.cohere(boids, matchCountRecip) +		// stick together
			this.avoid(boids, minDist) +			// avoid other boids
			this.matchVel(boids, matchCountRecip) +	// match velocity with nearby boids
			this.boundSpace(pos, limits, velMax);		// stay in the room
		
		centre.notNil.if({vel = vel + (centre - pos * 0.01)}); // move towards centering point
		vel = vel.clip2(velMax); // limit maximum velocity

		pos = (pos + vel) //.max(limits[0]).min(limits[1]);
	}
	
	cohere {|boids, matchCountRecip|
		var vec;
		vec = 0.0 ! dim;
		boids.reject({|boid| boid === this}).do({|boid| vec = vec + boid.pos });
		vec = vec * matchCountRecip;
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
	
	matchVel {|boids, matchCountRecip|
		var vec;
		vec = 0 ! dim;
		boids.do({|boid| 
			if(boid !== this, { vec = vec + boid.vel });
		});
		vec = vec * matchCountRecip;
		^(vec - vel * 0.125)
	}
	
	boundSpace{ |pos, limits, velMax|
		var vec;
		vec = 0 ! dim;
		pos.do({|dimension, i| 
			if(dimension < limits[0][i],  {vec[i] = velMax * 0.25});
			if(dimension > limits[1][i],  {vec[i] = velMax * -0.25});
		})
		^vec
	}
	
}

BMGrainBoidSpace {
	var <dim, <numBoids, <>centre, limits, <>velMax, <>minDist;
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
		boidStream.next.move(boids, centre, limits, velMax, minDist, matchCountRecip);
	}
	
}