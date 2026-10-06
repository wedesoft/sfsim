#version 450 core

uniform sampler2D points;
uniform mat4 camera_to_ndc;
uniform float scale;
uniform int overlay_width;
uniform int overlay_height;
uniform float shockwave_step;
uniform float shockwave_radius;

out vec4 fragColor;

vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float sampling_offset();
vec4 shockwave_transfer(vec3 p, float shockwave_step, vec4 shockwave_scatter);
vec4 geometry_point();

void main()
{
  vec3 origin = (camera_to_ndc * vec4(0, 0, 0, 1)).xyz * vec3(shockwave_radius, shockwave_radius, 2 * shockwave_radius);
  vec4 point = geometry_point();
  vec3 direction = (camera_to_ndc * vec4(point.xyz, 0)).xyz;
  direction = normalize(direction * vec3(1, 1, 2));
  vec2 segment = ray_box(vec3(-shockwave_radius, -shockwave_radius, 0),
                         vec3(shockwave_radius, shockwave_radius, 2 * shockwave_radius),
                         origin, direction);
  if (point.w > 0.0) {
    vec4 surface = camera_to_ndc * point * vec4(shockwave_radius, shockwave_radius, 2 * shockwave_radius, 1.0);
    float dist = length(surface.xyz - origin);
    if (segment.x + segment.y > dist) {
      segment.y = dist - segment.x;
    };
  };
  vec4 shockwave_scatter = vec4(0, 0, 0, 0);
  float x = segment.x + shockwave_step * shockwave_radius * sampling_offset();
  while (x < segment.x + segment.y) {
    vec3 point = origin + x * direction;
    vec3 scale = vec3(1.0 / shockwave_radius, 1.0 / shockwave_radius, 0.5 / shockwave_radius);
    shockwave_scatter = shockwave_transfer(point * scale, shockwave_step, shockwave_scatter);
    x += shockwave_step * shockwave_radius;
  };
  fragColor = shockwave_scatter;
}
