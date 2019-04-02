enum NoteState {note_on, note_off}

class MidiEvent {
  final NoteState noteState;
  final int midiNote;

  MidiEvent({this.noteState, this.midiNote});

  factory MidiEvent.fromJson(Map json){
    return MidiEvent(
      noteState: NoteState.values[json['noteState']],
      midiNote: json['midiNote'],
    );
  }

  @override
  String toString() {
    return 'MidiEvent{noteState: $noteState, midiNote: $midiNote}';
  }


}