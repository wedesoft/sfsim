#version 410 core

vec2 clip_interval(vec2 interval, vec2 clip);

vec4 clip_shell_intersections(vec4 intersections, vec2 clip)
{
  return vec4(clip_interval(intersections.xy, clip), clip_interval(intersections.zw, clip));
}
