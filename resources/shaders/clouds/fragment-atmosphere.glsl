#version 410 core

uniform vec3 origin;

in VS_OUT
{
  vec3 direction;
} fs_in;

out vec4 fragColor;

vec4 cloud_outer(vec3 origin, vec3 direction);

void main()
{
  fragColor = cloud_outer(origin, normalize(fs_in.direction));
}
