// result is a dict of (argname:value) which can be passed to newFromParamDict
BMControllerConfigGUI : BMAbstractGUI {
	var class, parent, okayFunc, params, widgets;
	
	*new {|class, parent, okayFunc|
		^super.new.init(class, parent, okayFunc).makeWindow;
	}
	
	init {|argclass, argparent, argokayFunc|
		class = argclass;
		parent = argparent;
		okayFunc = argokayFunc;
		params = class.parameterList;
		widgets = Array.new(params.size);
	}
	
	makeWindow {
		var result, textFields;
		result = ();
		textFields = List.new;
		window = SCModalSheet(parent, Rect(30, 30, 300, params.size + 1 * 24 + 44));
		window.addFlowLayout;
		StaticText(window, Rect(10, 10, 200, 20)).font_(Font("Helvetica-Bold", 12))
			.string_("Configure new" + class.humanName);
		params.keys.asArray.sort.do({|argName|
			var vals, widget, paramclass, lastValidInput;
			vals = params[argName]; // argname->[class, spec, humanName];
			paramclass = vals[0];
			if(paramclass == Integer || (paramclass == Float), {
				widget = EZNumber(window, 292@20, vals[2], vals[1], labelWidth: 100);
			}, {
				SCStaticText(window, 100@20).string_(vals[2]).align_(\right);
				widget = SCTextField(window, 188@20).string_(vals[1].value);
				textFields.add(widget);
			});
			
			if(paramclass != String && paramclass.superclasses.includes(RawArray), {
				lastValidInput = "";
				widget.action_({
					var interpretedInput;
					try {
						interpretedInput = 
							(paramclass.asString ++ "[" ++ widget.value ++ "]").postln.interpret;
						result[argName] = lastValidInput = interpretedInput;
					} {|error| 
						("Invalid input for array parameter" + vals[2] ++ ". Please re-enter.").error;
						widget.string = lastValidInput;
					};
				});
			}, {
				widget.action_({result[argName] = widget.value});
			});
			widget.doAction;
		});
		
		window.view.decorator.nextLine.nextLine;
		window.view.decorator.shift(window.bounds.width - 242, 0);
		
		RoundButton(window, 115 @ 20)
			.extrude_(false).canFocus_(false) 
			.states_([[ "Cancel", Color.black, Color.white.alpha_(0.8) ]])
			.action_({ window.close });
			   
		RoundButton(window, 115 @ 20)
			.extrude_(false).canFocus_(false)
			.states_([[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
			.action_({ 
				textFields.do({|tf| tf.doAction});
				window.close;
				okayFunc.value(result);
				onClose.value(this);
			});
		
		
	}
}