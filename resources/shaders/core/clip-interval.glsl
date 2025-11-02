#version 410 core

vec2 clip_interval(vec2 interval, vec2 clip)
{
  float start = max(interval.x, clip.x);
  float end = min(interval.x + interval.y, clip.x + clip.y);
  return vec2(start, end - start);
}
