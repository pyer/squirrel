// Squirrel application
// --------------------

// Name and version
const header = new Vue({
  el: '#header-left',
  data: {
    name: 'Squirrel',
    version: ''
  },
  created() {
    console.log("fetch version");
    fetch('/version').then(response => response.json())
                     .then(response => this.version = response.version);
    }
})

// Router

// 1. Define route components.
const Projects = {
  template: `
      <div>
        <table>
          <thead>
            <tr><th>Status</th><th>Name</th><th>Directory</th><th>Report</th></tr>
          </thead>
          <tbody>
            <tr v-for="project in projects"><td><img src="/images/24x24/green.png"/></td><td>{{project.name}}</td><td>{{project.dir}}</td><td><img src="/images/24x24/notepad.png"/></td></tr>
          </tbody>
        </table>
      </div>
      `,
  data() {
    return { projects: '' };
  },
  created() {
    console.log("fetch projects");
    fetch('/projects').then(response => response.json()).then(res => this.projects = res.projects);
  }
}

const Settings = {
  template: `
      <div>
        <table>
          <thead>
            <tr><th>Name</th><th>Value</th></tr>
          </thead>
          <tbody>
            <tr v-for="setting in settings"><td>{{setting.name}}</td><td>{{setting.value}}</td></tr>
          </tbody>
        </table>
      </div>
      `,
  data() {
    return { settings: '' };
  },
  created() {
    console.log("fetch settings");
    fetch('/settings').then(response => response.json()).then(res => this.settings = res.settings);
  }
}

const Bar = { template: '<div>bar</div>' }

//
//



// 2. Create the router instance and pass the `routes` option
// You can pass in additional options here, but let's
// keep it simple for now.
const router = new VueRouter({
  routes: [
    { path: '/projects', component: Projects },
    { path: '/settings', component: Settings },
    { path: '/builds',   component: Bar }
  ]
})

// 3. Create and mount the root instance.
// Make sure to inject the router with the router option to make the
// whole app router-aware.
const app = new Vue({
  router
}).$mount('#app')

// Now the app has started!
