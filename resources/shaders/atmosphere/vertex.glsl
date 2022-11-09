#version 410 core

uniform mat4 projection;
uniform mat4 transform;
in vec3 point;

out VS_OUT
{
  vec3 direction;
} vs_out;

// Simple vertex shader passing through coordinates of background quad for rendering the atmosphere.
void main()
{
  vs_out.direction = (transform * vec4(point, 0)).xyz;
  gl_Position = projection * vec4(point, 1);
}
