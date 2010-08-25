BMNoOpSpec {
	
	*new {
		^super.new;
	}
	
	*map { |val| ^val }
	
	*unmap { |val| ^val }
	
	*asSpec { ^this }
	
}