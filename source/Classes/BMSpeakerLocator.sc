BMSpeakerLocator {
	classvar <>originIn = 0, <>xin = 1, <>yin = 2, <>zin = 3, <>loopOut = 0, <>loopBackIn = 4;
	classvar <>running = false, <>signal = \impulse, <>speedOfSound = 344, repeats = 3;
	classvar <>waitTime = 1;
	classvar rout;
		
	*calcCart {|rOrigin, rX, rY, rZ, d|
		var x, y, z, dx, dy, dz;
		
		#dx, dy, dz = d;
		rOrigin = rOrigin.squared;
		rX = rX.squared;
		rY = rY.squared;
		rZ = rZ.squared; 
		x = (dx.squared +  rOrigin - rX) / (2 * dx);
		y = (dy.squared +  rOrigin - rY) / (2 * dy);
		z = (dz.squared +  rOrigin - rZ) / (2 * dz);
		^[x, y, z];
	}
	
	*run {|speakerList, micDeltas, okayFunc, server, onlyFullRange = true, ignoreZ = false|
		var target, buffer, duration, recordBuf;
		var sourceSig, sourceMax, sourceMaxInd, maxQual;
		var metersPerSample;
		running.if({
			"Speaker Locator already running; ignoring request".warn;
			^nil
		});
		if(micDeltas.size == 0, {micDeltas = micDeltas ! 3});
		
		server = server ? Server.default;
		target = server.asTarget;
		
		metersPerSample = speedOfSound / server.sampleRate;

		speakerList = speakerList.deepCopy;
		
		
		rout = {
			running = true;
			CmdPeriod.doOnce({this.stop});
			this.sendDef(server);
			#sourceSig, buffer = this.sendTestSignal(server);
			#sourceMaxInd, sourceMax, maxQual = this.quadInterpMax(sourceSig);
			maxQual.sign.switch(
				-1, {"good max found".postln },
				1, {"min found (qual: %), aborting\n".postf(maxQual); this.stop;},
				0, {"bad estimate (qual: %), aborting\n".postf(maxQual); this.stop; }
			);
			server.sync;
			duration = buffer.duration;
			recordBuf = Buffer.alloc(server, buffer.numFrames * 1.2, 5);
			currentEnvironment[\foo] = recordBuf;
			server.sync;
			
			"\n\\\\\\\\\\\\\\\ Auto Localise Starting\n".postln;
			speakerList.associationsDo({|speaker, index|
				var synth, waiting, o, x, y, z, l;
				var loopBackDelay, originDist, xDist, yDist, zDist;
				var cart, sphers, azi, ele, rad;
				
				if(speaker.isBMSpeaker and: {speaker.spec.fullRange || onlyFullRange.not}, {
					repeats.do({
						synth = Synth("BMAutoLocate", 
							[out: speaker, sigBuf: buffer, recBuf: recordBuf]
						);
						(duration + waitTime).wait;
						synth.free;
						waiting = Condition(false);
						recordBuf.loadToFloatArray(action: {|col|
							#o, x, y, z, l = col.unlace(5).collect(_.as(Signal));
							waiting.test = true; waiting.signal;
						});
						waiting.wait;
						// look always for maxItem not abs to avoid big phase inverted ripples
						loopBackDelay = this.quadInterpMax(l)[0];
						originDist = this.quadInterpMax(o)[0] - loopBackDelay * metersPerSample;
						xDist = (this.quadInterpMax(x)[0] - loopBackDelay) * metersPerSample;
						yDist = (this.quadInterpMax(y)[0] - loopBackDelay) * metersPerSample;
						zDist = (this.quadInterpMax(z)[0] - loopBackDelay) * metersPerSample;
						cart = this.calcCart(*([originDist, xDist, yDist, zDist, micDeltas]));
						cart.postln;
						// radius and azi will be right, but ele wrong
						ignoreZ.if({cart[2] = 0;}); 
						sphers = sphers.add(this.cart2spher(*cart));
					});
					
				}, {"% not a normal Speaker, skipping it...\n".postf(speaker.key)});
				
				// possibly check here for outliers and discard
				
				#azi, ele, rad = sphers.postln.flop.collect(_.mean); // azi, ele, rad
				
				speaker.azi = azi;
				speaker.ele = ele;
				speaker.rad = rad;
				"% coords: azimuth - %, elevation - %, radius - %\n"
					.postf(speaker.name, azi, ele, rad);
				waitTime.wait;
				
			});
			running = false;
			
			"\n\\\\\\\\\\\\\\\ Auto Localise Done\n".postln;
			okayFunc.value(speakerList);
			
		}.fork;
	
	}
	
	*cart2spher {|x, y, z|
		var azi, ele, rad, rad2deg;
		rad2deg = 360.0 / ( 2 * pi );
		azi = atan2(x, y) * rad2deg;
		rad = (x.squared + y.squared + z.squared).sqrt;
		ele = atan2(z, hypot(x, y)) * rad2deg;
		^[azi, ele, rad]
	}
	
	// trims only copied at the end, so we can abort safely
	*stop {
		running.if({
			rout.stop;
			running = false;
			"Auto Localise aborted".warn;
		});
	}
	
	*quadInterpMax {|array|
		// ye is value, xe is interpolated index, d2 is neg for maximum, pos for min, or 0 for bad
		var xc, yl, yc, yu;
		var d1,d2, xe, ye;
		var result; 
		  
		xc = array.maxIndex;
		yc = array[xc];
		yl = array[xc - 1];
		yu = array[xc + 1];
		  
		d2 = yu-yc+yl-yc; 
		d1 = 0.5*(yu-yl); 
		 
		if (d2 != 0.0, { 
			xe = xc - (d1/d2); 
			ye = yc + (0.5*d1*(xe-xc)); 
			if (abs(xc-xe)>1,{ result = [xe, ye, 0]; \bad.postln; }, // Reliability test 
				{ result = [xe, ye, d2]});
			}, { // Degenerate d2 
				xe = xc; // This could be NAN 
				ye = yc; // This could be NAN 
				result = [xe, ye, 0]; \bad.postln;
			}); 
		^result;
	}	
	
	*sendTestSignal {|server|
		var sr, sig, buffer;
		sr = server.sampleRate;
		signal.switch(
			\sweep, {
				var lowF = 400, hiF = 2000, dur = 0.1, phase = 0.0;
				sig = Signal.newClear(sr * dur);
				sig.waveFill({|x, i|
					var out;
					out = sin(phase);
					phase = phase + (x / sr * 2pi);
					out
				}, lowF, hiF);
				
				sig = Env.sine.asSignal(sr * dur) * sig;
				
				buffer = Buffer.sendCollection(server, sig);
			},
			\mls, {
				var lowF = 800, hiF = 4000, phase = 0.0;
				var m, n = 16, taps, smallm, regout, comp;
				taps = [0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1];
				m = 2.pow(n) - 1;
				
				smallm = 1 ! n;
				
				regout = 0 ! m;
				
				m.do({|i|
					var buf;
					buf = mod(sum(taps*smallm), 2);
					smallm = [smallm[0]] ++ smallm.drop(-1);
					smallm[0] = buf;
					regout[i] = smallm.last;
				});
				
				comp = regout.collect({|val|val.booleanValue.not.binaryValue});
				
				sig = regout - comp;
				
				buffer = Buffer.sendCollection(server, sig);
			},
			\impulse, {
				var lowF = 800, hiF = 4000, sr = 44100, dur = 0.1, phase = 0.0;
				sig = Signal.newClear(sr * dur);
				sig[1000] = 1;
				buffer = Buffer.sendCollection(server, sig);
			}
		);
		^[sig, buffer];
	}

	
	*sendDef {|server|
		SynthDef("BMAutoLocate", {|out, sigBuf, recBuf|
			var ins, output;
			ins = [originIn, xin, yin, zin, loopBackIn].collect(SoundIn.ar(_));
			RecordBuf.ar(ins, recBuf, doneAction: 2, loop: 0);
			output = PlayBuf.ar(1, sigBuf) * 0.7;			
			Out.ar(out, output);
			Out.ar(loopOut, output);
		}).send(server);
	
	}

		
}