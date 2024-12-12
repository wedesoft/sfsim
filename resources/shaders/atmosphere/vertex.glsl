#version 410 core

uniform mat4 projection;
uniform mat4 camera_to_world;
in vec3 point;

out VS_OUT
{
  vec3 direction;
} vs_out;

// Simple vertex shader passing through coordinates of background quad for rendering the atmosphere.
void main()
{
  vs_out.direction = (camera_to_world * vec4(point, 0)).xyz;
  vec4 position = projection * vec4(point, 1);
  position.z = max(position.z, 0.0);  // Handle numerical errors at farside of reversed-z rendering space.
  gl_Position = position;
}
