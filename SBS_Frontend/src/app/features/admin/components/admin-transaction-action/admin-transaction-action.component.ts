import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-admin-transaction-action',
  templateUrl: './admin-transaction-action.component.html',
  styleUrls: ['./admin-transaction-action.component.css']
})
export class AdminTransactionActionComponent implements OnInit {
  action: string | null = null;
  id: string | null = null;

  constructor(private route: ActivatedRoute, private router: Router) { }

  ngOnInit(): void {
    this.action = this.route.snapshot.paramMap.get('action');
    this.id = this.route.snapshot.paramMap.get('id');
  }

  performAction(): void {
    // Placeholder for actual action
    console.log(`${this.action} on transaction ID: ${this.id}`);
    this.router.navigate(['/transactions']);
  }
}
