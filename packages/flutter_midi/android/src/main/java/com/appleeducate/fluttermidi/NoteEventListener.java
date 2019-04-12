package com.appleeducate.fluttermidi;

import org.jfugue.Note;
import org.jfugue.ParserListenerAdapter;

import io.flutter.plugin.common.MethodChannel;

class NoteEventListener extends ParserListenerAdapter {
    final MethodChannel.Result result;

    NoteEventListener(MethodChannel.Result result) {
        this.result = result;
    }


    @Override
    public void noteEvent(Note note) {
        result.success(note.getValue());
        super.noteEvent(note);
    }
}