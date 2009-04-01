BMControllerGUI : BMAbstractGUI {
	var class, parent, params, widgets;
	
	*new {|class, parent|
		^super.new.init(class, parent).makeWindow;
	}
	
	init {|argclass, argparent|
		class = argclass;
		parent = argparent;
		params = class.parameterList;
		widgets = Array.new(params.size);
	}
	
	makeWindow {

		window = SCModalSheet(parent, Rect(30, 30, 300, params.size + 1 * 24));
		window.addFlowLayout;
		params.keys.asArray.sort.do({|argName|
			var vals, widget;
			vals = params[argName]; // argname->[class, spec, humanName];
			if(vals[0] == Integer || (vals[0] == Float), {
				widget = EZNumber(window, 292@20, vals[2], vals[1]);
			}, {
				SCStaticText(window, 140@20).string_(vals[1]);
				widget = SCTextField(window, 52@20);
			});
			
		});
	}
}