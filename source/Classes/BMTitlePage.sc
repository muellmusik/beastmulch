BMTitlePage {

	*initClass {
		String.scDir.contains("BEASTmulch System.app").if({ ApplicationStart.add(this) });
	}
	
	*doOnApplicationStart {
		var screenBounds, titleWindow;
		screenBounds = SCWindow.screenBounds;

		{
		
		titleWindow = SCWindow("Welcome", Rect(screenBounds.width / 2 - 350, screenBounds.height / 2 - 262,  700, 524), false, false)
			.alwaysOnTop_(true).front;
		
		SCQuartzComposerView(titleWindow, 700@524)
			.path_(String.scDir ++ "/SCClassLibrary/source/Title.qtz")
			.start;
		
		5.wait;
		titleWindow.close;
		}.fork(AppClock)

	}
}