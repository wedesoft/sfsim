#version 450 core

uniform vec3 origin;
uniform mat4 camera_to_world;
uniform float object_distance;

vec4 geometry_point();
float geometry_distance();
vec4 cloud_point(vec3 origin, vec3 direction, vec2 segment);

out vec4 fragColor;

void main()
{
  float dist = geometry_distance();
<% (if front %>
  vec3 direction = (camera_to_world * geometry_point()).xyz;
  fragColor = cloud_point(origin, direction, vec2(0, min(dist, object_distance)));
<% %>
  if (dist < object_distance)
    fragColor = vec4(0, 0, 0, 0);
  else {
    vec3 direction = (camera_to_world * geometry_point()).xyz;
    fragColor = cloud_point(origin, direction, vec2(object_distance, dist - object_distance));
  };
<% ) %>
}
