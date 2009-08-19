BMGrainBoid {
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

BMGrainBoidSpace {
	var <dim, <numBoids, <>centre, boundaries, <>velMax, <>velScale, <>minDist, <>avoidD = false;
	var <boids, boidStream, countRecip;
	
	*new {|dim = 3, numBoids, centre, boundaries, velMax, velScale, minDist, avoidD = false| 
		// boundaries is an array of [[min * dim], [max * dim]]
		^super.newCopyArgs(dim, numBoids, centre, boundaries, velMax, velScale ? 1.0, 
			minDist, avoidD).init;
	}
	
	init {
		boids = { BMGrainBoid(dim, minDist: minDist) } ! numBoids;
		boidStream = Pseq(boids, inf).asStream;
		countRecip = (numBoids - 1).reciprocal;
	}
	
	// move and return a boid
	moveNext {
		^boidStream.next.move(boids, centre, boundaries, velMax, velScale, minDist, avoidD, countRecip);
	}
	
}