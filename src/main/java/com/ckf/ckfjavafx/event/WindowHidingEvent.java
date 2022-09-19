package com.ckf.ckfjavafx.event;

import de.felixroske.jfxsupport.AbstractFxmlView;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WindowHidingEvent {
    private AbstractFxmlView view;
}
