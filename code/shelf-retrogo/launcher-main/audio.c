// Synthesizes square, triangle and noise voices and hands them to retro-go's
// audio, which applies the system volume and drives the speaker.
#include <math.h>
#include <string.h>
#include <stdlib.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"
#include <rg_system.h>
#include "audio.h"

#define RATE 32000        // must match the rate passed to rg_system_init()
#define CHUNK 256
#define VOICES 24

typedef struct {
    bool on, is_noise;
    int wave;
    uint32_t start, len;          // in samples
    float f0, f1, vol, phase, hold;
    uint32_t seed;
} voice_t;

static voice_t voices[VOICES];          // owned by the audio task
static QueueHandle_t incoming;          // new voices from other tasks
static volatile uint32_t clock_samples;
static float master = 0.3f;

void sound_set_enabled(bool on) { master = on ? 0.3f : 0.0f; }

static void add(voice_t v)
{
    v.on = true;
    if (incoming) xQueueSend(incoming, &v, 0);
}

void sound_tone(float f, float dur, int wave, float at, float vol, float slide)
{
    voice_t v = {0};
    v.wave = wave; v.f0 = f; v.f1 = slide > 0 ? slide : f; v.vol = vol;
    v.start = clock_samples + (uint32_t)(at * RATE) + CHUNK;
    v.len = (uint32_t)(dur * RATE);
    add(v);
}
void sound_noise(float dur, float at, float vol, float f0, float f1)
{
    voice_t v = {0};
    v.is_noise = true; v.f0 = f0; v.f1 = f1; v.vol = vol; v.seed = 12345 + clock_samples;
    v.start = clock_samples + (uint32_t)(at * RATE) + CHUNK;
    v.len = (uint32_t)(dur * RATE);
    add(v);
}

static void audio_task(void *arg)
{
    static rg_audio_frame_t out[CHUNK];
    while (1) {
        uint32_t now = clock_samples;
        float acc[CHUNK];
        memset(acc, 0, sizeof(acc));
        voice_t nv;
        while (xQueueReceive(incoming, &nv, 0) == pdTRUE)
            for (int i = 0; i < VOICES; i++) if (!voices[i].on) { voices[i] = nv; break; }
        for (int i = 0; i < VOICES; i++) {
            voice_t *v = &voices[i];
            if (!v->on) continue;
            if (now + CHUNK <= v->start) continue;
            for (int s = 0; s < CHUNK; s++) {
                uint32_t t = now + s;
                if (t < v->start) continue;
                uint32_t k = t - v->start;
                if (k >= v->len) { v->on = false; break; }
                float p = (float)k / v->len;
                float f = v->f0 * powf(v->f1 / v->f0, p);
                float env = (k < 110 ? k / 110.0f : 1.0f) * (1.0f - p) * (1.0f - p);
                float smp;
                if (v->is_noise) {
                    v->phase += f / RATE;
                    if (v->phase >= 1.0f) { v->phase -= floorf(v->phase); v->seed = v->seed * 1103515245 + 12345; v->hold = ((v->seed >> 16) & 0x7FFF) / 16384.0f - 1.0f; }
                    smp = v->hold;
                } else {
                    v->phase += f / RATE;
                    v->phase -= floorf(v->phase);
                    smp = v->wave == WAVE_SQUARE ? (v->phase < 0.5f ? 1.0f : -1.0f) : (4.0f * fabsf(v->phase - 0.5f) - 1.0f);
                }
                acc[s] += smp * env * v->vol;
            }
        }
        for (int s = 0; s < CHUNK; s++) {
            float x = acc[s] * master;
            if (x > 1) x = 1;
            if (x < -1) x = -1;
            int16_t v = (int16_t)(x * 32767.0f);
            out[s].left = v;
            out[s].right = v;
        }
        rg_audio_submit(out, CHUNK);   // blocks until there's room, which paces this loop
        clock_samples += CHUNK;
    }
}

void sound_init(void)
{
    incoming = xQueueCreate(32, sizeof(voice_t));
    xTaskCreatePinnedToCore(audio_task, "shelf_audio", 4096, NULL, 5, NULL, 1);
}

/* ---------- the sound palette, same as the prototype ---------- */
void sfx_tick(int d) { sound_tone(d > 0 ? 1320 : 1180, 0.035f, WAVE_SQUARE, 0, 0.45f, 0); }
void sfx_bump(void) { sound_tone(150, 0.1f, WAVE_TRIANGLE, 0, 0.9f, 90); }
void sfx_shelf(void) { sound_noise(0.2f, 0, 0.5f, 500, 2600); sound_tone(520, 0.09f, WAVE_TRIANGLE, 0.03f, 0.5f, 820); }
void sfx_open(void) { sound_tone(660, 0.06f, WAVE_SQUARE, 0, 0.45f, 0); sound_tone(990, 0.09f, WAVE_SQUARE, 0.05f, 0.45f, 0); }
void sfx_back(void) { sound_tone(880, 0.06f, WAVE_SQUARE, 0, 0.4f, 0); sound_tone(587, 0.09f, WAVE_SQUARE, 0.05f, 0.4f, 0); }
void sfx_fav(bool on)
{
    static const float up[] = {784, 988, 1175, 1568}, down[] = {1175, 988, 784};
    const float *n = on ? up : down;
    int c = on ? 4 : 3;
    for (int i = 0; i < c; i++) sound_tone(n[i], 0.07f, WAVE_SQUARE, i * 0.055f, 0.42f, 0);
}
void sfx_sort(void) { for (int i = 0; i < 4; i++) { sound_tone(700 + i * 90, 0.03f, WAVE_SQUARE, i * 0.045f, 0.35f, 0); sound_noise(0.03f, i * 0.045f, 0.25f, 3000, 1500); } }
void sfx_clunk(void) { sound_noise(0.08f, 0, 0.9f, 1800, 300); sound_tone(110, 0.14f, WAVE_TRIANGLE, 0, 1.0f, 60); }
void sfx_jingle(void)
{
    static const float n[6][2] = {{523, 0}, {659, 0.1f}, {784, 0.2f}, {1047, 0.3f}, {988, 0.45f}, {1047, 0.55f}};
    for (int i = 0; i < 6; i++) { float d = i == 5 ? 0.38f : 0.1f; sound_tone(n[i][0], d, WAVE_SQUARE, n[i][1], 0.4f, 0); sound_tone(n[i][0] / 2, d, WAVE_TRIANGLE, n[i][1], 0.6f, 0); }
}
void sfx_unjingle(void)
{
    static const float n[5][2] = {{1047, 0}, {988, 0.1f}, {784, 0.2f}, {659, 0.3f}, {523, 0.42f}};
    for (int i = 0; i < 5; i++) { float d = i == 4 ? 0.32f : 0.1f; sound_tone(n[i][0], d, WAVE_SQUARE, n[i][1], 0.36f, 0); sound_tone(n[i][0] / 2, d, WAVE_TRIANGLE, n[i][1], 0.5f, 0); }
}
void sfx_pop(void) { sound_tone(240, 0.14f, WAVE_TRIANGLE, 0, 0.9f, 760); sound_noise(0.05f, 0, 0.5f, 2400, 900); }
void sfx_whoosh(void) { sound_noise(0.28f, 0, 0.35f, 500, 3200); }
void sfx_saved(void) { sound_tone(1320, 0.05f, WAVE_SQUARE, 0, 0.32f, 0); sound_tone(1760, 0.09f, WAVE_SQUARE, 0.06f, 0.32f, 0); }
void sfx_letter(int i) { static const float n[] = {523, 587, 659, 784, 1047}; sound_tone(n[i < 5 ? i : 4], 0.1f, WAVE_SQUARE, 0, 0.42f, 0); }
void sfx_vol(void) { sound_tone(880, 0.05f, WAVE_SQUARE, 0, 0.6f, 0); }
