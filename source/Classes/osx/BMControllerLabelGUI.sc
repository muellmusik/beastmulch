BMControllerLabelGUI : BMAbstractGUI {
	var <controllers;

	*new {|name = "Edit Controller Labels", controllers|
		^super.new.init(name, controllers).makeWindow;
	}

	init {|argName, argControllers|
		controllers = argControllers;
		name = argName;
	}

	makeWindow {
		var mainLayout;
		window = Window(name, Rect(30, 30, 300, 600), scroll: true).alwaysOnTop_(true);
		window.layout = mainLayout = VLayout();
		controllers.do({|controller|
			var controllerLayout, mappedTo;
			if(controller.hasLabels, {
				mainLayout.add(controllerLayout = VLayout());
				controllerLayout.add(StaticText().string_(controller.name).font_(Font(size: 18, bold: true)));
				controller.controls.do({|control, i|
					var controlLayout, staticText, textField, tooltip;
					controllerLayout.add(controlLayout = HLayout(
						[staticText = StaticText().string_(control.name), stretch: 2],
						[textField = TextField(window).string_(controller.getLabel(i+1)).action_({|field| controller.setLabel(i+1, field.value);}), stretch: 3]
					));
					mappedTo = control.mappedTo;
					if(control.mappedTo.notNil, {
						tooltip = control.mappedTo.mappings[control.name].as(Array).asString;
						textField.toolTip = tooltip;
						staticText.toolTip = tooltip;
					});
				});
			});
		});
		window.front;
	}

}