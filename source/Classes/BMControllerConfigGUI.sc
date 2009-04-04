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
		var result;
		result = ();
		window = SCModalSheet(parent, Rect(30, 30, 300, params.size + 1 * 24));
		window.addFlowLayout;
		params.keys.asArray.sort.do({|argName|
			var vals, widget, paramclass, lastValidInput;
			vals = params[argName]; // argname->[class, spec, humanName];
			paramclass = vals[0];
			if(paramclass == Integer || (paramclass == Float), {
				widget = EZNumber(window, 292@20, vals[2], vals[1], labelWidth: 100);
			}, {
				SCStaticText(window, 100@20).string_(vals[2]).align_(\right);
				widget = SCTextField(window, 188@20);
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
		
		window.onClose = {
			okayFunc.value(result);
		}
		
	}
}