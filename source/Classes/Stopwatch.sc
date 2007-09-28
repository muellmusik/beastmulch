// latency allows for syncing with server bundled messages

// should cmdPeriod allow for resume?
// needs tests for stop, etc.
// should milliseconds be dealt with as a precision, or keep an 'actual' seconds and return seconds and milliseconds as needed

Stopwatch {

	var >func, <>time, resolution, <>latency, task, now, lastnow, pauseDelta = 0, <rate = 1;
	var <hours, <minutes, <seconds, <milliseconds; // updated every resolution period
	var started = false, running = false;

	*new {|func, time = 0, resolution = 1.0, latency = 0| 
		^super.newCopyArgs(func, time, resolution, latency) 
	}
	
	start { 
		if(running.not, {
			if(started.not, {
				task = Task.new({
					latency.wait;
					time = 0;
					lastnow = Main.elapsedTime;
					loop({
						pauseDelta = 0;
						now = Main.elapsedTime;
						this.updateTime;
						func.value(hours, minutes, seconds, milliseconds, time, this);
						lastnow = now;
						resolution.wait;
					})
				});
				CmdPeriod.add(this);
				task.start;
				started = true;
				running = true;
			}, {
				// resuming
				lastnow = Main.elapsedTime - pauseDelta;
				task.resume;
				running = true;
			});
		}, {"Already started".warn});
	}
	
	stop {
		if(running, {
			CmdPeriod.remove(this);
			task.stop;
		});
		started = false;
		running = false;
	}
	
	pause {
		if(running, {
		// in case of multiple pauses between task evaluations
			pauseDelta = Main.elapsedTime - lastnow + pauseDelta;
			task.pause;
			running = false;
		});
	}
	
	updateTime { // assumes now has just been set
		time = now - lastnow * rate + time;
		minutes = (time/60).trunc(1);
		if(minutes >= 60,{ hours = (minutes/60).trunc(1);
			minutes = minutes%60;
		},{
			hours = 0;
		});
		seconds = (time%60).trunc;
		milliseconds = (time%60).frac * 1000;
	}
	
//	getTimeString {
//		var string;
//		if(hours == 0, {string = "00:"}, {string = hours.asString ++ ":" });
//		if(minutes < 10, {string = string ++ "0" ++ minutes ++ ":"}, 
//			{string = string ++ minutes ++ ":"; });
//		if(seconds<10,{string = string ++ "0" ++ (seconds + (milliseconds * 0.001))},
//			{string = string ++ (seconds + (milliseconds * 0.001))});
//		^string;
//	}
	
	cmdPeriod { // make sure seconds gets updated 
		CmdPeriod.remove(this);
		// not sure about next two lines
		now = Main.elapsedTime;
		this.updateTime;
		started = false;
		running = false;
	}
	
	// this will have jitter, consider using TempoClock instead
	rate_ {|newRate|
		SystemClock.sched(latency, {rate = newRate});
	}

}