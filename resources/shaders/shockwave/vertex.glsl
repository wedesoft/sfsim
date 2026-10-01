#version 450 core

uniform mat4 projection;
uniform mat4 ndc_to_camera;

in vec3 point;

void main()
{
  gl_Position = projection * ndc_to_camera * vec4(point, 1);
}
