// Controls order of multiple Matrices
// Takes an Array of Matrices and sets their groups in corresponding order within the target group
// starting at the tail. Bundling ensures ordering.

// Note that AbstractMatrix-cmdPeriod calls this.changed, so any dependencies will be updated before the bundle is sent. This may not be desirable, and possibly should be factored out

BMAudioChainManager {
	var <matrices, <group;

	*new {|matrices, group|
		^super.newCopyArgs(matrices, group.asGroup).init; // default target is default Server
	}
	
	init {
		CmdPeriod.add(this);
		group.server.makeBundle(nil, {
			matrices.do({|matrix| matrix.callCmdPeriod_(false); matrix.group.moveToTail(group)});
		});
	}
	
	cmdPeriod {
		group.server.makeBundle(nil, {
			matrices.do({|matrix| matrix.cmdPeriod; matrix.group.moveToTail(group)});
		});
	}
	
	remove { |reactivateCP = false|
		CmdPeriod.remove(this);
		reactivateCP.if({ matrices.do({|matrix| matrix.callCmdPeriod_(true);}) })
	}

}