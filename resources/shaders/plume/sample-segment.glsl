#version 450 core

uniform float <%= type %>_step;

vec2 <%= type %>_box(vec3 origin, vec3 direction);
vec4 <%= type %>_transfer(vec3 point, float <%= type %>_step, vec4 <%= type %>_scatter);
float sampling_offset();
vec2 limit_interval(vec2 interval, float limit);

<% (when outer %>
vec4 sample_<%= type %>_outer(vec3 object_origin, vec3 object_direction)
<% ) %>
<% (when (not outer) %>
vec4 sample_<%= type %>_point(vec3 object_origin, vec3 object_direction, float dist)
<% ) %>
{
  vec2 segment = <%= type %>_box(object_origin, object_direction);
<% (when (not outer) %>
  segment = limit_interval(segment, dist);
<% ) %>
  vec4 result = vec4(0, 0, 0, 1);
  float x = segment.x + sampling_offset() * <%= type %>_step;
  while (x < segment.x + segment.y) {
    vec3 point = object_origin + x * object_direction;
    result = <%= type %>_transfer(point, <%= type %>_step, result);
    x += <%= type %>_step;
  };
  return vec4(result.rgb, 1 - result.a);
}
