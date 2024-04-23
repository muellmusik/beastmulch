BMControllerLabelGUI : BMAbstractGUI {
	var <controllers, textFields;

	*new {|name = "Edit Controller Labels", controllers|
		^super.new.init(name, controllers).makeWindow;
	}

	init {|argName, argControllers|
		controllers = argControllers;
		name = argName;
		textFields = IdentityDictionary[];
	}

	makeWindow {
		var mainLayout;
		window = Window(name, Rect(30, 30, 300, 600), scroll: true).alwaysOnTop_(true);
		window.view.canvas = View();
		window.view.canvas.layout = mainLayout = VLayout();
		mainLayout.add(Button().states_([["Default Labels"]]).action_({
			controllers.do({|controller|
				if(controller.hasLabels, { controller.setAllLabels(controller.defaultLabels) })
			});
		}));
		controllers.do({|controller|
			var controllerLayout, mappedTo;
			if(controller.hasLabels, {
				controller.addDependant(this);
				mainLayout.add(controllerLayout = VLayout());
				controllerLayout.add(StaticText().string_(controller.name).font_(Font(size: 18, bold: true)));
				controller.controls.do({|control, i|
					var controlLayout, staticText, textField, tooltip;
					controllerLayout.add(controlLayout = HLayout(
						[staticText = StaticText().string_(control.name), stretch: 2],
						[textField = TextField(window).string_(controller.getLabel(i+1)).action_({|field| controller.setLabel(i+1, field.value);}), stretch: 3]
					));
					textField.focusLostAction = { textField.doAction };
					textFields[controller] = textFields[controller].add(textField);
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
		this.onClose_({ textFields.keys.do({|controller| controller.removeDependant(this);}); textFields = nil});
	}

	update {|changer, changed, controlNum|
		if(changed == \label, { textFields[changer][controlNum].string = changer.getLabel(controlNum + 1) });
	}
}