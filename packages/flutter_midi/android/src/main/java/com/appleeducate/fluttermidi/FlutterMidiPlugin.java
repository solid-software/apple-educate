package com.appleeducate.fluttermidi;

import android.os.AsyncTask;

import com.pdrogfer.mididroid.MidiFile;
import com.pdrogfer.mididroid.event.MidiEvent;
import com.pdrogfer.mididroid.event.NoteOff;
import com.pdrogfer.mididroid.event.NoteOn;
import com.pdrogfer.mididroid.event.ProgramChange;
import com.pdrogfer.mididroid.event.meta.EndOfTrack;
import com.pdrogfer.mididroid.event.meta.KeySignature;
import com.pdrogfer.mididroid.event.meta.Tempo;
import com.pdrogfer.mididroid.event.meta.TimeSignature;
import com.pdrogfer.mididroid.util.MetronomeTick;
import com.pdrogfer.mididroid.util.MidiEventListener;
import com.pdrogfer.mididroid.util.MidiProcessor;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.sherlock.com.sun.media.sound.SF2Soundbank;
import cn.sherlock.com.sun.media.sound.SoftSynthesizer;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import jp.kshoji.javax.sound.midi.InvalidMidiDataException;
import jp.kshoji.javax.sound.midi.MidiUnavailableException;
import jp.kshoji.javax.sound.midi.Receiver;
import jp.kshoji.javax.sound.midi.ShortMessage;
import jp.kshoji.javax.sound.midi.VoiceStatus;

import org.jfugue.MidiRenderer;
import org.jfugue.MusicStringParser;
import org.jfugue.Pattern;
import org.jfugue.extras.Midi2JFugue;


/**
 * FlutterMidiPlugin
 */

public class FlutterMidiPlugin implements MethodCallHandler, MidiEventListener, EventChannel.StreamHandler {
    private SoftSynthesizer synth;
    private Receiver recv;
    private MidiProcessor midiProcessor;
    private Result methodResult;
    private MidiEventListener midiEventListener;
    private final static int DEFAULT_MIDI_VOLUME_LEVEL = 100;
    private boolean isPaused = false;

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_midi");
        FlutterMidiPlugin midiPlugin = new FlutterMidiPlugin();
        channel.setMethodCallHandler(midiPlugin);

        final EventChannel eventChannel = new EventChannel(registrar.messenger(), "midi_event_channel");
        eventChannel.setStreamHandler(midiPlugin);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        switch (call.method) {
            case "prepare_midi":
                String path = call.argument("path");
                final Result finalResult = result;
                AsyncTask<String, String, String> task = new AsyncTask<String, String, String>() {
                    @Override
                    protected String doInBackground(String... strings) {
                        try {
                            File _file = new File(strings[0]);
                            SF2Soundbank sf = new SF2Soundbank(_file);
                            synth = new SoftSynthesizer();
                            synth.open();
                            synth.loadAllInstruments(sf);
                            synth.getChannels()[0].programChange(0);
                            synth.getChannels()[1].programChange(1);
                            recv = synth.getReceiver();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (MidiUnavailableException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(String string) {
                        super.onPostExecute(string);
                        finalResult.success(true);
                    }
                };
                task.execute(path);
                break;
            case "change_sound":
                try {
                    String _path = call.argument("path");
                    File _file = new File(_path);
                    SF2Soundbank sf = new SF2Soundbank(_file);
                    synth = new SoftSynthesizer();
                    synth.open();
                    synth.loadAllInstruments(sf);
                    synth.getChannels()[0].programChange(0);
                    synth.getChannels()[1].programChange(1);
                    recv = synth.getReceiver();
                    result.success(true);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (MidiUnavailableException e) {
                    e.printStackTrace();
                }
                break;
            case "play_midi_note": {
                int _note = call.argument("note");
                try {
                    ShortMessage msg = new ShortMessage();
                    msg.setMessage(ShortMessage.NOTE_ON, 0, _note, 127);
                    recv.send(msg, -1);
                    result.success(true);
                } catch (InvalidMidiDataException e) {
                    e.printStackTrace();
                }
                break;
            }
            case "stop_midi_note": {
                int _note = call.argument("note");
                try {
                    ShortMessage msg = new ShortMessage();
                    msg.setMessage(ShortMessage.NOTE_OFF, 0, _note, 127);
                    recv.send(msg, -1);
                    result.success(true);
                } catch (InvalidMidiDataException e) {
                    e.printStackTrace();
                }
                break;
            }
            case "prepare_midi_file": {
                byte[] midiData = call.argument("midi_file");
                try {
                    prepareMidiFile(midiData);
                    result.success(true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            case "resume_midi_file": {
                boolean paused = call.argument("paused");
                if (midiProcessor == null) {
                    result.success(null);
                    return;
                }
                if (!paused) {
                    methodResult = result;
                }
                midiProcessor.registerEventListener(this, MidiEvent.class);
                midiProcessor.start();
                if (paused) result.success(synth.getLatency());
                break;
            }
            case "play_midi_file": {
                byte[] midiData = call.argument("midi_file");
                try {
                    prepareMidiFile(midiData);
                    methodResult = result;
                    midiProcessor.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            case "stop_playing":
                if (midiProcessor != null && midiProcessor.isRunning()) {
                    for (VoiceStatus status : synth.getVoiceStatus()) {
                        if (!status.active) continue;

                        ShortMessage noteOffMessage = new ShortMessage();
                        try {
                            noteOffMessage.setMessage(ShortMessage.NOTE_OFF, status.channel, status.note, 127);
                            recv.send(noteOffMessage, -1);
                        } catch (InvalidMidiDataException e) {
                            e.printStackTrace();
                        }
                    }
                    ShortMessage stopMessage = new ShortMessage();
                    try {
                        stopMessage.setMessage(ShortMessage.STOP);
                        recv.send(stopMessage, -1);
                    } catch (InvalidMidiDataException e) {
                        e.printStackTrace();
                    }
                    midiProcessor.unregisterAllEventListeners();
                    midiProcessor.stop();
                }
                isPaused = true;
                synth.getChannels()[0].controlChange(7, DEFAULT_MIDI_VOLUME_LEVEL);
                result.success(true);
                break;
            case "reset_processor":
                if (midiProcessor != null) {
                    midiProcessor.reset();
                    midiProcessor = null;
                    isPaused = false;
                }
                result.success(null);
                break;
            case "get_notes": {
                byte[] midiData = call.argument("midi_file");
                try {
                    InputStream midiFileInputStream = new ByteArrayInputStream(midiData);
                    MidiFile midiFile = new MidiFile(midiFileInputStream);
                    List<Integer> midiNotes = new ArrayList<>();
                    int midiTracksCount = midiFile.getTrackCount();
                    if (midiTracksCount > 0) {
                        Object[] midiEvents = midiFile.getTracks().get(midiTracksCount - 1).getEvents().toArray();
                        for (Object event : midiEvents) {
                            if (event instanceof NoteOn) {
                                midiNotes.add(((NoteOn) event).getNoteValue());
                            }
                        }
                    }
                    result.success(midiNotes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            case "get_note_value":
                String noteString = call.argument("note_string");
                NoteEventListener parserListener = new NoteEventListener(result);
                MusicStringParser parser = new MusicStringParser();
                parser.addParserListener(parserListener);
                Pattern pattern = new Pattern(noteString);
                parser.parse(pattern);
                break;
            case "change_volume_level":
                int volumeLevel = call.argument("volume_level");
                synth.getChannels()[0].controlChange(7, volumeLevel);
                result.success(true);
                break;
        }
    }

    private void prepareMidiFile(byte[] midiData) throws IOException {
        InputStream midiInputStream = new ByteArrayInputStream(midiData);
        MidiFile midiFile = new MidiFile(midiInputStream);
        if (midiProcessor == null) {
            midiProcessor = new MidiProcessor(midiFile);
        }
        midiProcessor.registerEventListener(this, MidiEvent.class);
    }

    @Override
    public void onStart(boolean b) {
    }

    @Override
    public void onEvent(MidiEvent midiEvent, long l) {
        if (methodResult != null) {
            methodResult.success(synth.getLatency());
            methodResult = null;
        }
        if (midiEvent instanceof NoteOn) {
            NoteOn note = (NoteOn) midiEvent;
            ShortMessage startMessage = new ShortMessage();
            try {
                startMessage.setMessage(ShortMessage.NOTE_ON, 0, note.getNoteValue(), note.getVelocity());
                recv.send(startMessage, -1);
            } catch (InvalidMidiDataException e) {
                e.printStackTrace();
            }
        } else if (midiEvent instanceof NoteOff) {
            NoteOff note = (NoteOff) midiEvent;
            ShortMessage stopMessage = new ShortMessage();
            try {
                stopMessage.setMessage(ShortMessage.NOTE_OFF, 0, note.getNoteValue(), note.getVelocity());
                recv.send(stopMessage, -1);
            } catch (InvalidMidiDataException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onStop(boolean b) {

    }


    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        final EventChannel.EventSink midiEventSink = eventSink;
        midiEventListener = new MidiEventListener() {
            @Override
            public void onStart(boolean b) {

            }

            @Override
            public void onEvent(MidiEvent midiEvent, long l) {
                if (midiEvent instanceof NoteOn) {
                    Map<String, Integer> resultMap = new HashMap<>();
                    NoteOn note = (NoteOn) midiEvent;
                    // If there is a NoteOn event with zero velocity it acts as a NoteOff
                    // event and that is why we should sent it to the flutter as a NoteOff event
                    if (note.getVelocity() != 0) {
                        resultMap.put("noteState", 0);
                        resultMap.put("midiNote", ((NoteOn) midiEvent).getNoteValue());
                    } else {
                        resultMap.put("noteState", 1);
                        resultMap.put("midiNote", ((NoteOn) midiEvent).getNoteValue());
                    }
                    midiEventSink.success(resultMap);
                } else if (midiEvent instanceof NoteOff) {
                    Map<String, Integer> resultMap = new HashMap<>();
                    resultMap.put("noteState", 1);
                    resultMap.put("midiNote", ((NoteOff) midiEvent).getNoteValue());
                    midiEventSink.success(resultMap);
                }
            }

            @Override
            public void onStop(boolean b) {

            }
        };
        midiProcessor.registerEventListener(midiEventListener, MidiEvent.class);
    }

    @Override
    public void onCancel(Object o) {
        if (midiEventListener != null && midiProcessor != null) {
            midiProcessor.unregisterEventListener(midiEventListener);
        }
    }
}
