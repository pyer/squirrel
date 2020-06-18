
var app = angular.module('squirrelApp', ["ngRoute"]);

app.config(function($routeProvider) {
    $routeProvider
    .when("/projects", {
        templateUrl : "projects.html",
        controller : "projectsCtrl"
    })
    .when("/settings", {
        templateUrl : "settings.html"
    })
    .when("/people", {
      template : "<div id=\"projects\">People</div>"
    })
    .when("/builds", {
        template : "<div id=\"projects\">Builds</div>"
    })
    .otherwise({
        template : "<div>Not found</div>"
    });

});

app.controller('projectsCtrl', function($scope,$http) {
    $http.get("/data/projects")
            .then(function(response) {
        $scope.projectsList = response.data;
        console.log($scope.projectsList);
    });
});
