#version 450 core

uniform sampler2D points;
uniform mat4 camera_to_shadow;
uniform float scale;
uniform int overlay_width;
uniform int overlay_height;
uniform float shockwave_step;
uniform float shockwave_radius;

out vec4 fragColor;

vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
vec4 shockwave_transfer(vec3 p, float shockwave_step, vec4 shockwave_scatter);
vec4 geometry_point();
float sampling_offset();
vec2 limit_interval(vec2 interval, float limit);

void main()
{
  vec3 scale = vec3(shockwave_radius, shockwave_radius, 2 * shockwave_radius);
  vec3 origin = (camera_to_shadow * vec4(0, 0, 0, 1)).xyz;
  vec4 point = geometry_point();
  vec3 direction = (camera_to_shadow * vec4(point.xyz, 0)).xyz;
  vec2 segment = ray_box(vec3(-shockwave_radius, -shockwave_radius, 0),
                         vec3(shockwave_radius, shockwave_radius, 2 * shockwave_radius),
                         origin, direction);
  if (point.w > 0.0) {
    vec3 surface = (camera_to_shadow * point).xyz;
    float dist = length(surface - origin);
    segment = limit_interval(segment, dist);
  };
  vec4 shockwave_scatter = vec4(0, 0, 0, 0);
  float x = segment.x + shockwave_step * shockwave_radius * sampling_offset();
  while (x < segment.x + segment.y) {
    vec3 sample_point = origin + x * direction;
    shockwave_scatter = shockwave_transfer(sample_point, shockwave_step, shockwave_scatter);
    x += shockwave_step * shockwave_radius;
  };
  fragColor = shockwave_scatter;
}
