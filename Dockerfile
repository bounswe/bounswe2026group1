FROM nginx:alpine
# Copies it to default serving directory of nginx
COPY . /usr/share/nginx/html 
EXPOSE 80