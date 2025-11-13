#version 410 core

uniform vec3 origin;
uniform vec3 object_origin;

in VS_OUT
{
  vec3 direction;
  vec3 object_direction;
} fs_in;

out vec4 fragColor;

vec4 cloud_plume_cloud(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction);

void main()
{
  fragColor = cloud_plume_cloud(origin, normalize(fs_in.direction), object_origin, normalize(fs_in.object_direction));
}
