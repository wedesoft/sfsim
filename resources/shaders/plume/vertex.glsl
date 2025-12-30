#version 450 core

uniform mat4 projection;
uniform mat4 object_to_camera;
uniform mat4 <%= type %>_to_object;

in vec3 point;

vec3 <%= type %>_box_size();

void main()
{
  vec3 box = <%= type %>_box_size();
  vec3 corner = mix(vec3(box.y, -box.x, -box.x), vec3(box.z, box.x, box.x), 0.5 * (point + 1));
  gl_Position = projection * object_to_camera * <%= type %>_to_object * vec4(corner, 1);
}
